package com.staffportal.staff_management_portal.service;

import com.staffportal.staff_management_portal.model.Attendance;
import com.staffportal.staff_management_portal.model.Department;
import com.staffportal.staff_management_portal.model.Employee;
import com.staffportal.staff_management_portal.model.User;
import com.staffportal.staff_management_portal.repository.EmployeeRepository;
import com.staffportal.staff_management_portal.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AttendanceAnomalyService {

    private static final int HALF_DAY_THRESHOLD = 3;
    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    private static final int LOOKBACK_DAYS = 30;
    private static final int LATE_THRESHOLD = 3;
    private static final int ABSENT_THRESHOLD = 3;
    private static final double LOW_PRESENCE_THRESHOLD = 0.70;

    // ---- Public entry points, scoped by role ----

    public List<Map<String, Object>> getAnomaliesForMe(String username) {
        Employee me = getMyEmployee(username);
        List<Attendance> records = attendanceService.getAttendanceByEmployee(me.getEmpId());
        Map<String, Object> result = analyzeOne(me, records);
        return result != null ? List.of(result) : List.of();
    }

    public List<Map<String, Object>> getAnomaliesForMyDepartment(String username) {
        Department dept = getMyDepartment(username);
        List<Employee> staff = employeeRepository.findByDepartment(dept);
        return analyzeGroup(staff);
    }

    public List<Map<String, Object>> getAnomaliesOrgWide() {
        List<Employee> all = employeeRepository.findAll();
        return analyzeGroup(all);
    }

    // ---- Core analysis logic ----

    private List<Map<String, Object>> analyzeGroup(List<Employee> employees) {
        List<Map<String, Object>> flagged = new ArrayList<>();
        for (Employee emp : employees) {
            List<Attendance> records = attendanceService.getAttendanceByEmployee(emp.getEmpId());
            Map<String, Object> result = analyzeOne(emp, records);
            if (result != null) {
                flagged.add(result);
            }
        }
        return flagged;
    }

    private Map<String, Object> analyzeOne(Employee emp, List<Attendance> records) {
        LocalDate cutoff = LocalDate.now().minusDays(LOOKBACK_DAYS);

        // "On Leave" days are approved absences, so they are not counted at all.
        List<Attendance> recent = records.stream()
                .filter(a -> a.getAttDate() != null && !a.getAttDate().isBefore(cutoff))
                .filter(a -> !"On Leave".equalsIgnoreCase(a.getStatus()))
                .collect(Collectors.toList());

        if (recent.isEmpty()) {
            return null; // not enough data to judge
        }

        long lateCount = recent.stream().filter(a -> "Late".equalsIgnoreCase(a.getStatus())).count();
        long absentCount = recent.stream().filter(a -> "Absent".equalsIgnoreCase(a.getStatus())).count();
        long halfDayCount = recent.stream().filter(a -> "Half Day".equalsIgnoreCase(a.getStatus())).count();
        long presentCount = recent.stream().filter(a -> "Present".equalsIgnoreCase(a.getStatus())).count();
        double presenceRate = (double) presentCount / recent.size();

        List<String> reasons = new ArrayList<>();
        if (lateCount >= LATE_THRESHOLD) {
            reasons.add("Late " + lateCount + " times in the last " + LOOKBACK_DAYS + " days");
        }
        if (absentCount >= ABSENT_THRESHOLD) {
            reasons.add("Absent " + absentCount + " times in the last " + LOOKBACK_DAYS + " days");
        }
        if (halfDayCount >= HALF_DAY_THRESHOLD) {
            reasons.add("Half Day " + halfDayCount + " times in the last " + LOOKBACK_DAYS + " days (late arrivals or missed checkouts)");
        }
        if (presenceRate < LOW_PRESENCE_THRESHOLD) {
            reasons.add(String.format("Low attendance rate: %.0f%% present", presenceRate * 100));
        }

        if (reasons.isEmpty()) {
            return null; // no anomaly
        }

        Map<String, Object> result = new HashMap<>();
        result.put("empId", emp.getEmpId());
        result.put("name", emp.getName());
        result.put("employeeCode", emp.getEmployeeCode());
        result.put("department", emp.getDepartment());
        result.put("lateCount", lateCount);
        result.put("absentCount", absentCount);
        result.put("presenceRatePercent", Math.round(presenceRate * 100));
        result.put("reasons", reasons);
        return result;
    }

    // ---- Helpers ----

    private Employee getMyEmployee(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        if (user.getEmployee() == null) {
            throw new RuntimeException("This account is not linked to an employee record.");
        }
        return user.getEmployee();
    }

    private Department getMyDepartment(String username) {
        return getMyEmployee(username).getDepartment();
    }
}