package com.staffportal.staff_management_portal.controller;

import com.staffportal.staff_management_portal.model.Payroll;
import com.staffportal.staff_management_portal.model.PayrollDeductionDetail;
import com.staffportal.staff_management_portal.service.PayrollService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payroll")
public class PayrollController {

    @Autowired
    private PayrollService payrollService;

    @PostMapping
    public ResponseEntity<?> generatePayroll(@RequestBody PayrollCreateRequest request) {
        try {
            Payroll payroll = payrollService.generatePayroll(
                    request.getEmpId(),
                    request.getMonth(),
                    request.getBasicSalary(),
                    request.getDeductionLines()
            );
            return ResponseEntity.ok(payroll);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public List<Payroll> getAllPayrolls() {
        return payrollService.getAllPayrolls();
    }

    @GetMapping("/me")
    public List<Payroll> getMyPayroll(Authentication authentication) {
        return payrollService.getMyPayroll(authentication.getName());
    }

    @GetMapping("/me/{payrollId}/deduction-details")
    public ResponseEntity<?> getMyDeductionDetails(Authentication authentication, @PathVariable Long payrollId) {
        try {
            return ResponseEntity.ok(payrollService.getMyDeductionDetails(authentication.getName(), payrollId));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    @GetMapping("/employee/{empId}")
    public List<Payroll> getPayrollsByEmployee(@PathVariable Long empId) {
        return payrollService.getPayrollsByEmployee(empId);
    }

    @GetMapping("/{payrollId}/deduction-details")
    public List<PayrollDeductionDetail> getDeductionDetails(@PathVariable Long payrollId) {
        return payrollService.getDeductionDetails(payrollId);
    }

    @DeleteMapping("/{payrollId}")
    public String deletePayroll(@PathVariable Long payrollId) {
        boolean deleted = payrollService.deletePayroll(payrollId);
        return deleted ? "Payroll record deleted successfully." : "Payroll record not found.";
    }

    // ---- Request DTO ----
    public static class PayrollCreateRequest {
        private Long empId;
        private String month;
        private Double basicSalary;
        private List<PayrollService.DeductionLine> deductionLines;

        public Long getEmpId() { return empId; }
        public void setEmpId(Long empId) { this.empId = empId; }
        public String getMonth() { return month; }
        public void setMonth(String month) { this.month = month; }
        public Double getBasicSalary() { return basicSalary; }
        public void setBasicSalary(Double basicSalary) { this.basicSalary = basicSalary; }
        public List<PayrollService.DeductionLine> getDeductionLines() { return deductionLines; }
        public void setDeductionLines(List<PayrollService.DeductionLine> deductionLines) { this.deductionLines = deductionLines; }
    }

    @GetMapping("/suggest-deductions")
    public ResponseEntity<?> suggestDeductions(
            @RequestParam Long empId,
            @RequestParam String month,
            @RequestParam int year) {
        try {
            return ResponseEntity.ok(payrollService.suggestDeductions(empId, month, year));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }
}