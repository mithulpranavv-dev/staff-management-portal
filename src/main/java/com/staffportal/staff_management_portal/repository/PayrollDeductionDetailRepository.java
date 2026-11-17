package com.staffportal.staff_management_portal.repository;

import com.staffportal.staff_management_portal.model.PayrollDeductionDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayrollDeductionDetailRepository extends JpaRepository<PayrollDeductionDetail, Long> {
    List<PayrollDeductionDetail> findByPayroll_PayrollId(Long payrollId);
}