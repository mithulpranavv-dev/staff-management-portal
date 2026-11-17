package com.staffportal.staff_management_portal.repository;

import com.staffportal.staff_management_portal.model.Department;
import com.staffportal.staff_management_portal.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findByDepartment(Department department);
}