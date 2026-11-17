package com.staffportal.staff_management_portal.service;

import com.staffportal.staff_management_portal.model.Attendance;
import com.staffportal.staff_management_portal.model.Department;
import com.staffportal.staff_management_portal.model.Employee;
import com.staffportal.staff_management_portal.model.Task;
import com.staffportal.staff_management_portal.model.TaskAssignment;
import com.staffportal.staff_management_portal.model.User;
import com.staffportal.staff_management_portal.repository.AttendanceRepository;
import com.staffportal.staff_management_portal.repository.EmployeeRepository;
import com.staffportal.staff_management_portal.repository.TaskRepository;
import com.staffportal.staff_management_portal.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PerformanceService {

    private static final int ATTENDANCE_LOOKBACK_DAYS = 30;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private UserRepository userRepository;

    // Dean: everyone who works on tasks (Dean and Administrator are left out)
    @Transactional(readOnly = true)
    public Map<String, Object> forAll() {
        return build(workingEmployees(employeeRepository.findAll()));
    }

    // HOD: own department only
    @Transactional(readOnly = true)
    public Map<String, Object> forMyDepartment(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        if (user.getEmployee() == null || user.getEmployee().getDepartment() == null) {
            throw new RuntimeException("No department is assigned to this account.");
        }
        Department dept = user.getEmployee().getDepartment();
        return build(workingEmployees(employeeRepository.findByDepartment(dept)));
    }

    private List<Employee> workingEmployees(List<Employee> employees) {
        Set<Long> skip = new HashSet<>();
        for (User u : userRepository.findAll()) {
            if (u.getEmployee() == null || u.getRole() == null) continue;
            String r = u.getRole().name();
            if ("DEAN".equals(r) || "ADMINISTRATOR".equals(r)) {
                skip.add(u.getEmployee().getEmpId());
            }
        }
        return employees.stream()
                .filter(e -> !skip.contains(e.getEmpId()))
                .collect(Collectors.toList());
    }

    private Integer pct(int part, int whole) {
        if (whole == 0) return null;
        return Math.round(100f * part / whole);
    }

    private Map<String, Object> build(List<Employee> employees) {
        // counters: [assigned, completed, onTime, pending, inProgress]
        Map<Long, int[]> counters = new HashMap<>();
        for (Employee e : employees) {
            counters.put(e.getEmpId(), new int[5]);
        }

        for (Task t : taskRepository.findAll()) {
            boolean completed = "Completed".equalsIgnoreCase(t.getStatus());
            boolean inProgress = "In Progress".equalsIgnoreCase(t.getStatus());
            boolean onTime = completed
                    && t.getCompletedDate() != null && t.getDueDate() != null
                    && t.getCompletedDate().compareTo(t.getDueDate()) <= 0;

            for (TaskAssignment a : t.getAssignments()) {
                if (a.getEmployee() == null) continue;
                int[] c = counters.get(a.getEmployee().getEmpId());
                if (c == null) continue;
                c[0]++;
                if (completed) c[1]++;
                if (onTime) c[2]++;
                if (!completed && !inProgress) c[3]++;
                if (inProgress) c[4]++;
            }
        }

        LocalDate cutoff = LocalDate.now().minusDays(ATTENDANCE_LOOKBACK_DAYS);
        List<Map<String, Object>> people = new ArrayList<>();
        int totAssigned = 0, totCompleted = 0, totOnTime = 0, totPresent = 0, totCounted = 0;

        for (Employee e : employees) {
            int[] c = counters.get(e.getEmpId());

            int present = 0, counted = 0;
            for (Attendance att : attendanceRepository.findByEmpId(e.getEmpId())) {
                if (att.getAttDate() == null || att.getAttDate().isBefore(cutoff)) continue;
                if ("On Leave".equalsIgnoreCase(att.getStatus())) continue;
                counted++;
                if ("Present".equalsIgnoreCase(att.getStatus())) present++;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("empId", e.getEmpId());
            row.put("name", e.getName());
            row.put("employeeCode", e.getEmployeeCode());
            row.put("department", e.getDepartment() != null ? String.valueOf(e.getDepartment()) : "No department");
            row.put("assigned", c[0]);
            row.put("completed", c[1]);
            row.put("onTime", c[2]);
            row.put("late", c[1] - c[2]);
            row.put("pending", c[3]);
            row.put("inProgress", c[4]);
            row.put("completionRate", pct(c[1], c[0]));
            row.put("onTimeRate", pct(c[2], c[1]));
            row.put("attendanceRate", pct(present, counted));
            people.add(row);

            totAssigned += c[0];
            totCompleted += c[1];
            totOnTime += c[2];
            totPresent += present;
            totCounted += counted;
        }

        people.sort(Comparator.comparing(m -> String.valueOf(m.get("name"))));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("people", people.size());
        summary.put("assigned", totAssigned);
        summary.put("completed", totCompleted);
        summary.put("onTime", totOnTime);
        summary.put("completionRate", pct(totCompleted, totAssigned));
        summary.put("onTimeRate", pct(totOnTime, totCompleted));
        summary.put("attendanceRate", pct(totPresent, totCounted));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("people", people);
        result.put("attendanceDays", ATTENDANCE_LOOKBACK_DAYS);
        return result;
    }
}