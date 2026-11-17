package com.staffportal.staff_management_portal.service;

import com.staffportal.staff_management_portal.model.Department;
import com.staffportal.staff_management_portal.model.Employee;
import com.staffportal.staff_management_portal.model.LeaveRequest;
import com.staffportal.staff_management_portal.model.User;
import com.staffportal.staff_management_portal.repository.EmployeeRepository;
import com.staffportal.staff_management_portal.repository.LeaveRequestRepository;
import com.staffportal.staff_management_portal.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LeaveStatsService {

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LeaveBalanceService leaveBalanceService;

    private Employee getMyEmployee(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        if (user.getEmployee() == null) {
            throw new RuntimeException("This account is not linked to an employee record.");
        }
        return user.getEmployee();
    }

    // ---- Scoped leave lists (shared by charts and details) ----

    private List<LeaveRequest> leavesForMe(String username) {
        return leaveRequestRepository.findByEmpId(getMyEmployee(username).getEmpId());
    }

    private List<LeaveRequest> leavesForMyDepartment(String username) {
        Department dept = getMyEmployee(username).getDepartment();
        if (dept == null) {
            throw new RuntimeException("No department is assigned to this account.");
        }
        List<Long> empIds = employeeRepository.findByDepartment(dept).stream()
                .map(Employee::getEmpId)
                .collect(Collectors.toList());
        return leaveRequestRepository.findByEmpIdIn(empIds);
    }

    // ---- Chart counts ----

    // Staff / HOD personal view
    public Map<String, Object> forMe(String username, int year) {
        Employee me = getMyEmployee(username);
        Map<String, Object> result = build(leavesForMe(username), year, null);
        result.put("balance", leaveBalanceService.getBalanceForEmployee(me.getEmpId(), year));
        return result;
    }

    // HOD department view
    public Map<String, Object> forMyDepartment(String username, int year) {
        return build(leavesForMyDepartment(username), year, null);
    }

    // Dean / Admin organization view
    public Map<String, Object> forAll(int year) {
        Map<Long, String> deptByEmp = employeeRepository.findAll().stream()
                .collect(Collectors.toMap(
                        Employee::getEmpId,
                        e -> e.getDepartment() != null ? String.valueOf(e.getDepartment()) : "No department",
                        (a, b) -> a));
        return build(leaveRequestRepository.findAll(), year, deptByEmp);
    }

    // ---- Detailed records (for click-to-expand) ----

    public List<Map<String, Object>> detailsForMe(String username) {
        return toDetails(leavesForMe(username));
    }

    public List<Map<String, Object>> detailsForMyDepartment(String username) {
        return toDetails(leavesForMyDepartment(username));
    }

    public List<Map<String, Object>> detailsForAll() {
        return toDetails(leaveRequestRepository.findAll());
    }

    private List<Map<String, Object>> toDetails(List<LeaveRequest> leaves) {
        Map<Long, Employee> employees = employeeRepository.findAll().stream()
                .collect(Collectors.toMap(Employee::getEmpId, e -> e, (a, b) -> a));

        List<LeaveRequest> sorted = new ArrayList<>(leaves);
        sorted.sort(Comparator.comparing(LeaveRequest::getFromDate,
                Comparator.nullsLast(Comparator.reverseOrder())));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (LeaveRequest l : sorted) {
            Employee e = employees.get(l.getEmpId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("leaveId", l.getLeaveId());
            row.put("empId", l.getEmpId());
            row.put("name", e != null ? e.getName() : "Employee #" + l.getEmpId());
            row.put("employeeCode", e != null ? e.getEmployeeCode() : null);
            row.put("department", e != null && e.getDepartment() != null
                    ? String.valueOf(e.getDepartment()) : "No department");
            row.put("leaveType", l.getLeaveType());
            row.put("fromDate", l.getFromDate() != null ? l.getFromDate().toString() : null);
            row.put("toDate", l.getToDate() != null ? l.getToDate().toString() : null);
            row.put("days", countDaysExcludingSundays(l.getFromDate(), l.getToDate()));
            row.put("status", l.getStatus());
            row.put("reason", l.getReason());
            row.put("rejectionReason", l.getRejectionReason());
            rows.add(row);
        }
        return rows;
    }

    private int countDaysExcludingSundays(LocalDate from, LocalDate to) {
        if (from == null || to == null) return 0;
        int count = 0;
        LocalDate day = from;
        while (!day.isAfter(to)) {
            if (day.getDayOfWeek() != DayOfWeek.SUNDAY) count++;
            day = day.plusDays(1);
        }
        return count;
    }

    private Map<String, Object> build(List<LeaveRequest> leaves, int year, Map<Long, String> deptByEmp) {
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        byStatus.put("Pending", 0);
        byStatus.put("Approved", 0);
        byStatus.put("Rejected", 0);

        Map<String, Integer> byType = new LinkedHashMap<>();
        Map<String, Integer> byDepartment = new LinkedHashMap<>();
        int[] months = new int[12];

        for (LeaveRequest l : leaves) {
            String status = l.getStatus() != null ? l.getStatus() : "Pending";
            byStatus.merge(status, 1, Integer::sum);

            String type = l.getLeaveType() != null ? l.getLeaveType() : "Other";
            byType.merge(type, 1, Integer::sum);

            if (l.getFromDate() != null && l.getFromDate().getYear() == year) {
                months[l.getFromDate().getMonthValue() - 1]++;
            }

            if (deptByEmp != null) {
                byDepartment.merge(deptByEmp.getOrDefault(l.getEmpId(), "No department"), 1, Integer::sum);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("totalRequests", leaves.size());
        result.put("byStatus", byStatus);
        result.put("byType", byType);
        result.put("byMonth", Arrays.stream(months).boxed().collect(Collectors.toList()));
        if (deptByEmp != null) {
            result.put("byDepartment", byDepartment);
        }
        return result;
    }
}