package com.staffportal.staff_management_portal.controller;

import com.staffportal.staff_management_portal.model.Department;
import com.staffportal.staff_management_portal.model.Employee;
import com.staffportal.staff_management_portal.model.LeaveRequest;
import com.staffportal.staff_management_portal.model.User;
import com.staffportal.staff_management_portal.repository.EmployeeRepository;
import com.staffportal.staff_management_portal.repository.LeaveRequestRepository;
import com.staffportal.staff_management_portal.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/on-leave")
public class OnLeaveController {

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    @GetMapping("/today")
    public ResponseEntity<?> today(Authentication auth) {
        boolean seesAll = hasRole(auth, "DEAN") || hasRole(auth, "ADMINISTRATOR");
        boolean isHod = hasRole(auth, "HEAD_OF_DEPARTMENT");
        if (!seesAll && !isHod) {
            return ResponseEntity.status(403).body(Map.of("error", "Not allowed."));
        }

        List<Employee> people;
        if (seesAll) {
            people = employeeRepository.findAll();
        } else {
            User user = userRepository.findByUsername(auth.getName()).orElse(null);
            if (user == null || user.getEmployee() == null || user.getEmployee().getDepartment() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "No department is assigned to this account."));
            }
            Department dept = user.getEmployee().getDepartment();
            people = employeeRepository.findByDepartment(dept);
        }

        Map<Long, Employee> byId = people.stream()
                .collect(Collectors.toMap(Employee::getEmpId, e -> e, (a, b) -> a));
        List<LeaveRequest> leaves = leaveRequestRepository.findByEmpIdIn(new ArrayList<>(byId.keySet()));

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LeaveRequest l : leaves) {
            if (!"Approved".equalsIgnoreCase(l.getStatus())) continue;
            if (l.getFromDate() == null || l.getToDate() == null) continue;
            if (today.isBefore(l.getFromDate()) || today.isAfter(l.getToDate())) continue;

            Employee e = byId.get(l.getEmpId());
            LocalDate back = l.getToDate().plusDays(1);
            if (back.getDayOfWeek() == DayOfWeek.SUNDAY) back = back.plusDays(1);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", e.getName());
            row.put("employeeCode", e.getEmployeeCode());
            row.put("department", e.getDepartment() != null ? String.valueOf(e.getDepartment()) : "No department");
            row.put("leaveType", l.getLeaveType());
            row.put("fromDate", l.getFromDate().toString());
            row.put("toDate", l.getToDate().toString());
            row.put("returnDate", back.toString());
            rows.add(row);
        }
        return ResponseEntity.ok(rows);
    }
}