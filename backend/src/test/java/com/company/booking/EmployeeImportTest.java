package com.company.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.company.booking.domain.Employee;
import com.company.booking.dto.Dtos.ImportResult;
import com.company.booking.service.EmployeeService;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:importdb;DB_CLOSE_DELAY=-1",
        "app.seed-demo-data=false"})
class EmployeeImportTest {

    @Autowired EmployeeService service;

    @Test
    void importsValidRowsReportsErrorsAndUpdatesOnReimport() {
        String csv = "ФИО;Email;Телефон\n"
                + "Иванов Иван;ivanov@test.ru;+7 900 111-22-33\n"
                + "Петров Пётр;PETROV@test.ru;\n"
                + "Без Почты;bad-email;\n"
                + "Дубль;ivanov@test.ru;\n";
        ImportResult res = service.importCsv(csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(2, res.created());
        assertEquals(2, res.errors().size());
        assertEquals(2, res.credentials().size());
        assertEquals(10, res.credentials().get(0).tempPassword().length());

        ImportResult again = service.importCsv(csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(0, again.created());
        assertEquals(2, again.updated());
        assertEquals(0, again.credentials().size());
    }

    @Test
    void readsWindows1251AndQuotedValues() {
        String csv = "\"Сидоров, Сидор\";sidorov@test.ru;\n";
        ImportResult res = service.importCsv(csv.getBytes(Charset.forName("windows-1251")));
        assertEquals(1, res.created());
        assertTrue(service.list().stream().anyMatch(e -> e.getFullName().equals("Сидоров, Сидор")));
    }

    @Test
    void authenticatesWithTempPasswordAndRejectsWrongOne() {
        ImportResult res = service.importCsv("Кузнецов Кузьма;kuznetsov@test.ru;\n".getBytes(StandardCharsets.UTF_8));
        String pwd = res.credentials().get(0).tempPassword();
        Employee ok = service.authenticate("Kuznetsov@test.ru", pwd);
        assertNotNull(ok);
        assertTrue(ok.isMustChangePassword());
        assertNull(service.authenticate("kuznetsov@test.ru", "wrong-password"));

        service.changePassword(ok.getId(), pwd, "NewSecret123");
        assertNotNull(service.authenticate("kuznetsov@test.ru", "NewSecret123"));
    }
}
