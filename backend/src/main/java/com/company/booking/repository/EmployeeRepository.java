package com.company.booking.repository;

import com.company.booking.domain.Employee;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByEmailIgnoreCase(String email);

    List<Employee> findAllByOrderByFullNameAsc();

    List<Employee> findByActiveTrueOrderByFullNameAsc();

    Optional<Employee> findByExternalId(String externalId);

    List<Employee> findByExternalIdIsNotNull();
}
