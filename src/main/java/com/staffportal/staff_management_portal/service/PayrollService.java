package com.staffportal.staff_management_portal.service;

import com.staffportal.staff_management_portal.model.Attendance;
import com.staffportal.staff_management_portal.model.Employee;
import com.staffportal.staff_management_portal.model.Payroll;
import com.staffportal.staff_management_portal.model.PayrollDeductionDetail;
import com.staffportal.staff_management_portal.model.User;
import com.staffportal.staff_management_portal.repository.AttendanceRepository;
import com.staffportal.staff_management_portal.repository.EmployeeRepository;
import com.staffportal.staff_management_portal.repository.PayrollDeductionDetailRepository;
import com.staffportal.staff_management_portal.repository.PayrollRepository;
import com.staffportal.staff_management_portal.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class PayrollService {

    @Autowired
    private PayrollRepository payrollRepository;

    @Autowired
    private PayrollDeductionDetailRepository deductionDetailRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private LeaveBalanceService leaveBalanceService;

    public Payroll generatePayroll(Long empId, String month, Double basicSalary,
                                   List<DeductionLine> deductionLines) {
        if (payrollRepository.existsByEmpIdAndMonth(empId, month)) {
            throw new RuntimeException("A payslip for this employee and month has already been generated.");
        }

        validateGenerationWindow(month);

        double totalDeductions = 0.0;
        if (deductionLines != null) {
            for (DeductionLine line : deductionLines) {
                totalDeductions += line.getAmount();
            }
        }

        Payroll payroll = new Payroll();
        payroll.setEmpId(empId);
        payroll.setMonth(month);
        payroll.setBasicSalary(basicSalary);
        payroll.setDeductions(totalDeductions);
        payroll.setNetSalary(basicSalary - totalDeductions);

        Payroll savedPayroll = payrollRepository.save(payroll);

        if (deductionLines != null) {
            for (DeductionLine line : deductionLines) {
                PayrollDeductionDetail detail = new PayrollDeductionDetail();
                detail.setPayroll(savedPayroll);
                detail.setDeductionDate(line.getDate());
                detail.setAmount(line.getAmount());
                detail.setReason(line.getReason());
                deductionDetailRepository.save(detail);
                savedPayroll.getDeductionDetails().add(detail);
            }
        }

        return savedPayroll;
    }

    private void validateGenerationWindow(String monthYearLabel) {
        String[] parts = monthYearLabel.trim().split(" ");
        if (parts.length != 2) {
            throw new RuntimeException("Invalid pay period format: " + monthYearLabel);
        }

        String monthName = parts[0];
        int year;
        try {
            year = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid year in pay period: " + monthYearLabel);
        }

        int monthNumber = java.time.Month.valueOf(monthName.toUpperCase()).getValue();
        java.time.YearMonth targetMonth = java.time.YearMonth.of(year, monthNumber);
        LocalDate today = LocalDate.now();

        LocalDate windowStart = targetMonth.atDay(targetMonth.lengthOfMonth() - 1);
        LocalDate windowEnd = targetMonth.atEndOfMonth();

        if (today.isBefore(windowStart) || today.isAfter(windowEnd)) {
            throw new RuntimeException(
                    "Payroll for " + monthYearLabel + " can only be generated on " +
                            windowStart + " or " + windowEnd + "."
            );
        }
    }

    public List<Payroll> getAllPayrolls() {
        return payrollRepository.findAll();
    }

    public List<Payroll> getPayrollsByEmployee(Long empId) {
        return payrollRepository.findByEmpId(empId);
    }

    public List<PayrollDeductionDetail> getDeductionDetails(Long payrollId) {
        return deductionDetailRepository.findByPayroll_PayrollId(payrollId);
    }

    public boolean deletePayroll(Long payrollId) {
        if (payrollRepository.existsById(payrollId)) {
            payrollRepository.deleteById(payrollId);
            return true;
        }
        return false;
    }

    // ---- Self-scoped access (for Staff portal) ----

    private Long resolveMyEmpId(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        if (user.getEmployee() == null) {
            throw new RuntimeException("This account is not linked to an employee record.");
        }
        return user.getEmployee().getEmpId();
    }

    public List<Payroll> getMyPayroll(String username) {
        Long myEmpId = resolveMyEmpId(username);
        return payrollRepository.findByEmpId(myEmpId);
    }

    public List<PayrollDeductionDetail> getMyDeductionDetails(String username, Long payrollId) {
        Long myEmpId = resolveMyEmpId(username);

        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll record not found"));

        if (!payroll.getEmpId().equals(myEmpId)) {
            throw new SecurityException("You do not have permission to view this payslip.");
        }

        return deductionDetailRepository.findByPayroll_PayrollId(payrollId);
    }

    // Small helper class to carry one deduction line from the controller
    public static class DeductionLine {
        private LocalDate date;
        private Double amount;
        private String reason;

        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public java.util.Map<String, Object> suggestDeductions(Long empId, String monthName, int year) {
        Employee employee = employeeRepository.findById(empId)
                .orElseThrow(() -> new RuntimeException("Employee not found: " + empId));

        int monthNumber = java.time.Month.valueOf(monthName.toUpperCase()).getValue();
        java.time.YearMonth yearMonth = java.time.YearMonth.of(year, monthNumber);
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();
        int daysInMonth = yearMonth.lengthOfMonth();

        Double monthlySalary = employee.getMonthlySalary();
        Double perDaySalary = employee.getPerDaySalary(daysInMonth);

        List<Attendance> records = attendanceRepository.findByEmpIdAndAttDateBetween(empId, monthStart, monthEnd);

        // Every date covered by approved leave (paid or unpaid): never charge Absent/Half Day on these.
        java.util.Set<LocalDate> leaveDays =
                new java.util.HashSet<>(leaveBalanceService.getApprovedLeaveDays(empId, year));

        List<java.util.Map<String, Object>> suggestedLines = new java.util.ArrayList<>();

        for (Attendance att : records) {
            if (leaveDays.contains(att.getAttDate())) {
                continue;
            }
            if ("Half Day".equals(att.getStatus()) && perDaySalary != null) {
                java.util.Map<String, Object> line = new java.util.HashMap<>();
                line.put("date", att.getAttDate().toString());
                line.put("amount", Math.round(perDaySalary / 2.0));
                line.put("reason", "Half Day - " + att.getAttDate());
                suggestedLines.add(line);
            } else if ("Absent".equals(att.getStatus()) && perDaySalary != null) {
                java.util.Map<String, Object> line = new java.util.HashMap<>();
                line.put("date", att.getAttDate().toString());
                line.put("amount", Math.round(perDaySalary));
                line.put("reason", "Absent - " + att.getAttDate());
                suggestedLines.add(line);
            }
        }

        // Unpaid leave: approved days beyond the yearly paid quota that fall in this month.
        if (perDaySalary != null) {
            for (LocalDate unpaidDay : leaveBalanceService.getUnpaidLeaveDays(empId, year)) {
                if (unpaidDay.isBefore(monthStart) || unpaidDay.isAfter(monthEnd)) {
                    continue;
                }
                java.util.Map<String, Object> line = new java.util.HashMap<>();
                line.put("date", unpaidDay.toString());
                line.put("amount", Math.round(perDaySalary));
                line.put("reason", "Unpaid leave (beyond paid quota) - " + unpaidDay);
                suggestedLines.add(line);
            }
        }

        suggestedLines.sort((a, b) -> ((String) a.get("date")).compareTo((String) b.get("date")));

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("monthlySalary", monthlySalary != null ? Math.round(monthlySalary) : null);
        result.put("perDaySalary", perDaySalary != null ? Math.round(perDaySalary) : null);
        result.put("suggestedDeductions", suggestedLines);
        result.put("leaveSummary", leaveBalanceService.getBalanceForEmployee(empId, year));
        return result;
    }
}