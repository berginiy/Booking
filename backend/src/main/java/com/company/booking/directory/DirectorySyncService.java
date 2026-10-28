package com.company.booking.directory;

import com.company.booking.config.AppProperties;
import com.company.booking.domain.Employee;
import com.company.booking.dto.Dtos.Credential;
import com.company.booking.dto.Dtos.ImportError;
import com.company.booking.dto.Dtos.SyncResult;
import com.company.booking.exception.ApiException;
import com.company.booking.repository.EmployeeRepository;
import com.company.booking.security.CurrentUser;
import com.company.booking.service.AuditService;
import com.company.booking.service.EmployeeService;
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
public class DirectorySyncService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9][0-9()\\-\\s]{6,19}$");

    private final GraphDirectoryClient client;
    private final EmployeeRepository repo;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final AppProperties props;

    public DirectorySyncService(GraphDirectoryClient client, EmployeeRepository repo, PasswordEncoder encoder,
                                AuditService audit, AppProperties props) {
        this.client = client;
        this.repo = repo;
        this.encoder = encoder;
        this.audit = audit;
        this.props = props;
    }

    @Transactional
    public SyncResult sync() {
        List<DirectoryUser> users = client.fetchUsers();
        if (users.isEmpty()) {
            throw ApiException.conflict("DIRECTORY_EMPTY",
                    "Каталог вернул пустой список. Синхронизация отменена, чтобы не отключить всех сотрудников");
        }

        String domain = props.corporateEmailDomain();
        List<ImportError> errors = new ArrayList<>();
        List<Credential> credentials = new ArrayList<>();
        Set<String> fetchedIds = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();
        int created = 0, updated = 0, disabled = 0, skipped = 0, line = 0;

        for (DirectoryUser u : users) {
            line++;
            if (u.externalId().isBlank()) {
                skipped++;
                continue;
            }
            fetchedIds.add(u.externalId());

            String email = u.email().trim().toLowerCase(Locale.ROOT);
            boolean foreign = domain != null && !domain.isBlank() && !email.endsWith("@" + domain.toLowerCase(Locale.ROOT));
            if (email.isEmpty() || foreign) {
                skipped++;
                continue;
            }
            String name = u.fullName().trim();
            if (name.isEmpty() || name.length() > 150) {
                errors.add(new ImportError(line, "Некорректное ФИО у " + email));
                continue;
            }
            if (!EMAIL.matcher(email).matches()) {
                errors.add(new ImportError(line, "Некорректный email: " + email));
                continue;
            }
            if (!seenEmails.add(email)) {
                errors.add(new ImportError(line, "Повтор email в каталоге: " + email));
                continue;
            }
            String phone = PHONE.matcher(u.phone()).matches() ? u.phone() : null;

            Employee existing = repo.findByExternalId(u.externalId())
                    .or(() -> repo.findByEmailIgnoreCase(email))
                    .orElse(null);

            if (existing == null) {
                String pwd = EmployeeService.generatePassword();
                Employee e = new Employee();
                e.setExternalId(u.externalId());
                e.setFullName(name);
                e.setEmail(email);
                e.setPhone(phone);
                e.setPasswordHash(encoder.encode(pwd));
                e.setMustChangePassword(true);
                e.setActive(u.enabled());
                repo.save(e);
                credentials.add(new Credential(name, email, pwd));
                created++;
                continue;
            }

            boolean emailTaken = repo.findByEmailIgnoreCase(email)
                    .filter(x -> !x.getId().equals(existing.getId()))
                    .isPresent();
            if (emailTaken) {
                errors.add(new ImportError(line, "Email уже занят другим сотрудником: " + email));
                continue;
            }
            existing.setExternalId(u.externalId());
            existing.setFullName(name);
            existing.setEmail(email);
            if (phone != null) existing.setPhone(phone);
            if (existing.isActive() != u.enabled()) {
                existing.setActive(u.enabled());
                if (!u.enabled()) disabled++;
            }
            updated++;
        }

        for (Employee e : repo.findByExternalIdIsNotNull()) {
            if (e.isActive() && !fetchedIds.contains(e.getExternalId())) {
                e.setActive(false);
                disabled++;
            }
        }

        audit.log(CurrentUser.adminName(), "EMPLOYEE_SYNC", "EMPLOYEE", null, "SUCCESS",
                "создано " + created + ", обновлено " + updated + ", отключено " + disabled
                        + ", пропущено " + skipped + ", ошибок " + errors.size());
        return new SyncResult(created, updated, disabled, skipped, errors, credentials);
    }
}
