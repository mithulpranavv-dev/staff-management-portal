package com.staffportal.staff_management_portal.controller;

import com.staffportal.staff_management_portal.model.Employee;
import com.staffportal.staff_management_portal.model.LeaveRequest;
import com.staffportal.staff_management_portal.model.Task;
import com.staffportal.staff_management_portal.service.SmartSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/search")
public class SmartSearchController {

    @Autowired
    private SmartSearchService smartSearchService;

    @GetMapping("/employees")
    public List<Map<String, Object>> searchEmployees(@RequestParam String q, Authentication authentication) {
        boolean showSalary = smartSearchService.canSeeSalary(authentication.getName());
        return smartSearchService.searchEmployees(authentication.getName(), q).stream()
                .map(e -> employeeToMap(e, showSalary))
                .collect(Collectors.toList());
    }

    @GetMapping("/tasks")
    public List<Map<String, Object>> searchTasks(@RequestParam String q, Authentication authentication) {
        return smartSearchService.searchTasks(authentication.getName(), q).stream()
                .map(this::taskToMap)
                .collect(Collectors.toList());
    }

    @GetMapping("/leaves")
    public List<LeaveRequest> searchLeaves(@RequestParam String q, Authentication authentication) {
        return smartSearchService.searchLeaves(authentication.getName(), q);
    }

    // Salary and email are shown only to the Dean and Administrator.
    private Map<String, Object> employeeToMap(Employee e, boolean showPrivate) {
        Map<String, Object> map = new HashMap<>();
        map.put("empId", e.getEmpId());
        map.put("name", e.getName());
        map.put("employeeCode", e.getEmployeeCode());
        map.put("department", e.getDepartment());
        map.put("designation", e.getDesignation());
        map.put("email", showPrivate ? e.getEmail() : null);
        map.put("salaryPerAnnum", showPrivate ? e.getSalaryPerAnnum() : null);
        map.put("monthlySalary", showPrivate ? e.getMonthlySalary() : null);
        return map;
    }

    private Map<String, Object> taskToMap(Task task) {
        Map<String, Object> map = new HashMap<>();
        map.put("taskId", task.getTaskId());
        map.put("title", task.getTitle());
        map.put("description", task.getDescription());
        map.put("priority", task.getPriority());
        map.put("dueDate", task.getDueDate());
        map.put("status", task.getStatus());
        map.put("completedDate", task.getCompletedDate());

        List<Map<String, Object>> assignees = task.getAssignments().stream()
                .map(a -> {
                    Map<String, Object> empMap = new HashMap<>();
                    empMap.put("empId", a.getEmployee().getEmpId());
                    empMap.put("name", a.getEmployee().getName());
                    empMap.put("employeeCode", a.getEmployee().getEmployeeCode());
                    return empMap;
                })
                .collect(Collectors.toList());
        map.put("assignedEmployees", assignees);
        return map;
    }
}