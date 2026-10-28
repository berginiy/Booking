package com.company.booking.service;

import com.company.booking.config.AppProperties;
import com.company.booking.domain.Employee;
import com.company.booking.dto.Dtos.Credential;
import com.company.booking.dto.Dtos.EmployeeRequest;
import com.company.booking.dto.Dtos.ImportError;
import com.company.booking.dto.Dtos.ImportResult;
import com.company.booking.exception.ApiException;
import com.company.booking.repository.EmployeeRepository;
import com.company.booking.security.CurrentUser;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9][0-9()\\-\\s]{6,19}$");
    private static final int MAX_IMPORT_ROWS = 2000;

    private final EmployeeRepository repo;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final AppProperties props;
    /** Для выравнивания времени ответа при неизвестном email (защита от перебора логинов). */
    private final String dummyHash;

    public EmployeeService(EmployeeRepository repo, PasswordEncoder encoder, AuditService audit, AppProperties props) {
        this.repo = repo;
        this.encoder = encoder;
        this.audit = audit;
        this.props = props;
        this.dummyHash = encoder.encode("dummy-password-for-timing");
    }

    // ------------------------------------------------------------ чтение

    @Transactional(readOnly = true)
    public List<Employee> list() {
        return repo.findAllByOrderByFullNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Employee> listActive() {
        return repo.findByActiveTrueOrderByFullNameAsc();
    }

    @Transactional(readOnly = true)
    public Employee get(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Сотрудник не найден"));
    }

    // ------------------------------------------------------------ администрирование

    public record Created(Employee employee, String tempPassword) {}

    @Transactional
    public Created create(EmployeeRequest r) {
        String email = r.email().trim().toLowerCase(Locale.ROOT);
        String err = emailError(email);
        if (err != null) throw ApiException.badRequest("VALIDATION", err);
        if (repo.findByEmailIgnoreCase(email).isPresent()) {
            throw ApiException.conflict("EMAIL_EXISTS", "Сотрудник с таким email уже существует");
        }
        String pwd = generatePassword();
        Employee e = newEmployee(r.fullName(), email, r.phone(), pwd);
        if (r.active() != null) e.setActive(r.active());
        e = repo.save(e);
        audit.log(CurrentUser.adminName(), "EMPLOYEE_CREATE", "EMPLOYEE", e.getId(), "SUCCESS", email);
        return new Created(e, pwd);
    }

    @Transactional
    public Employee update(Long id, EmployeeRequest r) {
        Employee e = get(id);
        String email = r.email().trim().toLowerCase(Locale.ROOT);
        String err = emailError(email);
        if (err != null) throw ApiException.badRequest("VALIDATION", err);
        if (repo.findByEmailIgnoreCase(email).filter(x -> !x.getId().equals(id)).isPresent()) {
            throw ApiException.conflict("EMAIL_EXISTS", "Сотрудник с таким email уже существует");
        }
        e.setFullName(r.fullName().trim());
        e.setEmail(email);
        e.setPhone(blankToNull(r.phone()));
        if (r.active() != null) e.setActive(r.active());
        audit.log(CurrentUser.adminName(), "EMPLOYEE_UPDATE", "EMPLOYEE", id, "SUCCESS",
                email + (e.isActive() ? "" : " (отключён)"));
        return e;
    }

    @Transactional
    public Created resetPassword(Long id) {
        Employee e = get(id);
        String pwd = generatePassword();
        e.setPasswordHash(encoder.encode(pwd));
        e.setMustChangePassword(true);
        audit.log(CurrentUser.adminName(), "EMPLOYEE_PASSWORD_RESET", "EMPLOYEE", id, "SUCCESS", e.getEmail());
        return new Created(e, pwd);
    }

    // ------------------------------------------------------------ импорт CSV

    /**
     * Формат: ФИО; email; телефон (заголовок необязателен). Разделитель ; , или табуляция,
     * кодировка UTF-8 или windows-1251 (как сохраняет Excel). Новые сотрудники создаются с временным паролем,
     * существующие (по email) — обновляются (ФИО, телефон), пароль не меняется.
     */
    @Transactional
    public ImportResult importCsv(byte[] data) {
        String text = decode(data);
        String[] lines = text.split("\\r?\\n");
        char sep = detectSeparator(lines);

        List<ImportError> errors = new ArrayList<>();
        List<Credential> credentials = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int created = 0, updated = 0, rows = 0;
        boolean first = true;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) continue;
            int lineNo = i + 1;
            List<String> cells = splitLine(line, sep);

            if (first) {
                first = false;
                // первая строка без '@' во втором столбце — заголовок
                if (cells.size() < 2 || !cells.get(1).contains("@")) continue;
            }
            if (++rows > MAX_IMPORT_ROWS) {
                errors.add(new ImportError(lineNo, "Превышен лимит " + MAX_IMPORT_ROWS + " строк за один импорт"));
                break;
            }
            if (cells.size() < 2) {
                errors.add(new ImportError(lineNo, "Ожидается: ФИО; email; телефон"));
                continue;
            }

            String name = cells.get(0).trim();
            String email = cells.get(1).trim().toLowerCase(Locale.ROOT);
            String phone = cells.size() > 2 ? cells.get(2).trim() : "";

            String err = null;
            if (name.isEmpty()) err = "Не указано ФИО";
            else if (name.length() > 150) err = "ФИО длиннее 150 символов";
            else if ((err = emailError(email)) != null) { /* сообщение уже в err */ }
            else if (!phone.isEmpty() && !PHONE.matcher(phone).matches()) err = "Некорректный телефон";
            else if (!seen.add(email)) err = "Повтор email в файле: " + email;
            if (err != null) {
                errors.add(new ImportError(lineNo, err));
                continue;
            }

            var existing = repo.findByEmailIgnoreCase(email);
            if (existing.isPresent()) {
                Employee e = existing.get();
                e.setFullName(name);
                if (!phone.isEmpty()) e.setPhone(phone);
                updated++;
            } else {
                String pwd = generatePassword();
                repo.save(newEmployee(name, email, phone, pwd));
                credentials.add(new Credential(name, email, pwd));
                created++;
            }
        }
        audit.log(CurrentUser.adminName(), "EMPLOYEE_IMPORT", "EMPLOYEE", null, "SUCCESS",
                "создано " + created + ", обновлено " + updated + ", ошибок " + errors.size());
        return new ImportResult(created, updated, errors.size(), errors, credentials);
    }

    // ------------------------------------------------------------ вход и пароль

    /** Возвращает сотрудника при верных данных и активной учётной записи, иначе null. */
    @Transactional(readOnly = true)
    public Employee authenticate(String email, String password) {
        Employee e = repo.findByEmailIgnoreCase(email.trim()).orElse(null);
        boolean ok = encoder.matches(password, e != null ? e.getPasswordHash() : dummyHash);
        return e != null && e.isActive() && ok ? e : null;
    }

    @Transactional
    public Employee changePassword(Long id, String current, String next) {
        Employee e = get(id);
        if (!encoder.matches(current, e.getPasswordHash())) {
            audit.log("employee:" + e.getEmail(), "PASSWORD_CHANGE", "EMPLOYEE", id, "FAILED", "Неверный текущий пароль");
            throw ApiException.badRequest("BAD_PASSWORD", "Неверный текущий пароль");
        }
        if (current.equals(next)) {
            throw ApiException.badRequest("VALIDATION", "Новый пароль должен отличаться от текущего");
        }
        e.setPasswordHash(encoder.encode(next));
        e.setMustChangePassword(false);
        audit.log("employee:" + e.getEmail(), "PASSWORD_CHANGE", "EMPLOYEE", id, "SUCCESS", null);
        return e;
    }

    // ------------------------------------------------------------ helpers

    private Employee newEmployee(String name, String email, String phone, String rawPassword) {
        Employee e = new Employee();
        e.setFullName(name.trim());
        e.setEmail(email);
        e.setPhone(blankToNull(phone));
        e.setPasswordHash(encoder.encode(rawPassword));
        e.setMustChangePassword(true);
        e.setActive(true);
        return e;
    }

    /** null — email корректен. email должен быть в нижнем регистре. */
    private String emailError(String email) {
        if (!EMAIL.matcher(email).matches()) return "Некорректный email: " + email;
        String d = props.corporateEmailDomain();
        if (d != null && !d.isBlank() && !email.endsWith("@" + d.toLowerCase(Locale.ROOT))) {
            return "Нужна корпоративная почта (@" + d + "): " + email;
        }
        return null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public static String generatePassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    /** UTF-8 (с BOM или без); если байты не валидны — windows-1251 (обычный «CSV» из русского Excel). */
    static String decode(byte[] data) {
        try {
            String s = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
            return s.startsWith("\uFEFF") ? s.substring(1) : s;
        } catch (CharacterCodingException ex) {
            return new String(data, Charset.forName("windows-1251"));
        }
    }

    private static char detectSeparator(String[] lines) {
        for (String l : lines) {
            if (l.isBlank()) continue;
            long semi = l.chars().filter(c -> c == ';').count();
            long comma = l.chars().filter(c -> c == ',').count();
            long tab = l.chars().filter(c -> c == '\t').count();
            if (semi >= comma && semi >= tab && semi > 0) return ';';
            if (tab > comma) return '\t';
            return ',';
        }
        return ';';
    }

    /** Разбор строки CSV с поддержкой значений в кавычках ("Иванов, Иван"; "" — экранированная кавычка). */
    static List<String> splitLine(String line, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else quoted = false;
                } else cur.append(c);
            } else if (c == '"') {
                quoted = true;
            } else if (c == sep) {
                out.add(cur.toString());
                cur.setLength(0);
            } else cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }
}
