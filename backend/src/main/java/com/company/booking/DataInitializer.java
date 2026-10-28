package com.company.booking;

import com.company.booking.config.AppProperties;
import com.company.booking.domain.Employee;
import com.company.booking.domain.Room;
import com.company.booking.repository.EmployeeRepository;
import com.company.booking.repository.RoomRepository;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Демо-данные (только если таблицы пусты): по 2 зала на этаж и один тестовый сотрудник. */
@Component
public class DataInitializer implements CommandLineRunner {

    private final RoomRepository rooms;
    private final EmployeeRepository employees;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public DataInitializer(RoomRepository rooms, EmployeeRepository employees, PasswordEncoder encoder, AppProperties props) {
        this.rooms = rooms;
        this.employees = employees;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        if (!props.seedDemoData()) return;
        seedRooms();
        seedEmployee();
    }

    private void seedRooms() {
        if (rooms.count() > 0) return;
        String[] names = {"Байкал", "Эльбрус", "Алтай", "Урал", "Кавказ", "Волга", "Енисей", "Лена", "Амур", "Нева"};
        int[] caps = {12, 6, 20, 8, 10, 4, 16, 6, 8, 30};
        for (int floor = 1; floor <= props.floors(); floor++) {
            for (int i = 0; i < 2; i++) {
                int idx = ((floor - 1) * 2 + i) % names.length;
                Room r = new Room();
                r.setName(names[idx]);
                r.setFloor(floor);
                r.setCapacity(caps[idx]);
                r.setDescription("Переговорная на " + floor + " этаже");
                r.setEquipment(new java.util.ArrayList<>(
                        i == 0 ? List.of("Проектор", "Маркерная доска", "Видеоконференцсвязь")
                               : List.of("Телевизор", "Маркерная доска")));
                rooms.save(r);
            }
        }
    }

    /** Тестовый сотрудник для быстрой проверки: ivanov@company.ru / Employee123 (отключается seed-demo-data: false). */
    private void seedEmployee() {
        if (employees.count() > 0) return;
        Employee e = new Employee();
        e.setFullName("Иванов Иван Иванович");
        e.setEmail("ivanov@company.ru");
        e.setPhone("+7 900 123-45-67");
        e.setPasswordHash(encoder.encode("Employee123"));
        e.setMustChangePassword(false);
        employees.save(e);
    }
}
