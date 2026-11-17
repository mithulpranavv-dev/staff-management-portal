package com.staffportal.staff_management_portal.repository;

import com.staffportal.staff_management_portal.model.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    List<Payroll> findByEmpId(Long empId);

    void deleteByEmpId(Long empId);

    boolean existsByEmpIdAndMonth(Long empId, String month);
}