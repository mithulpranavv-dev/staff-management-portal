package com.staffportal.staff_management_portal.controller;

import com.staffportal.staff_management_portal.model.Department;
import com.staffportal.staff_management_portal.model.Employee;
import com.staffportal.staff_management_portal.model.Task;
import com.staffportal.staff_management_portal.service.EmployeeService;
import com.staffportal.staff_management_portal.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private EmployeeService employeeService;

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    @GetMapping
    public List<Map<String, Object>> getAllTasks() {
        return taskService.getAllTasks().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/me")
    public List<Map<String, Object>> getMyTasks(Authentication authentication) {
        return taskService.getMyTasks(authentication.getName()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // HOD only: tasks where at least one assignee is in the HOD's own department
    @GetMapping("/my-department")
    public ResponseEntity<?> getMyDepartmentTasks(Authentication authentication) {
        if (!hasRole(authentication, "HEAD_OF_DEPARTMENT")) {
            return ResponseEntity.status(403).body(Map.of("error", "Only a Head of Department can view this."));
        }
        try {
            List<Map<String, Object>> tasks = taskService.getTasksForMyDepartment(authentication.getName()).stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(tasks);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/employee/{empId}")
    public List<Map<String, Object>> getTasksByEmployee(@PathVariable Long empId) {
        return taskService.getTasksByEmployee(empId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<?> createTask(@RequestBody TaskCreateRequest request, Authentication authentication) {
        if (request.getEmpIds() == null || request.getEmpIds().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Select at least one staff member."));
        }

        // An HOD may assign tasks only to people in their own department
        if (hasRole(authentication, "HEAD_OF_DEPARTMENT")) {
            Department dept = employeeService.getDepartmentForUsername(authentication.getName());
            Set<Long> deptIds = employeeService.getEmployeesByDepartment(dept).stream()
                    .map(Employee::getEmpId)
                    .collect(Collectors.toSet());
            if (!deptIds.containsAll(request.getEmpIds())) {
                return ResponseEntity.status(403)
                        .body(Map.of("error", "You can only assign tasks to staff in your own department."));
            }
        }

        try {
            Task task = taskService.assignTask(
                    request.getTitle(),
                    request.getDescription(),
                    request.getPriority(),
                    request.getDueDate(),
                    request.getEmpIds()
            );
            return ResponseEntity.ok(toResponse(task));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Only a person assigned to the task can change its status.
    @PutMapping("/{taskId}/status")
    public ResponseEntity<?> updateTaskStatus(@PathVariable Long taskId,
                                              @RequestBody Map<String, String> body,
                                              Authentication authentication) {
        boolean isAssigned = taskService.getMyTasks(authentication.getName()).stream()
                .anyMatch(t -> t.getTaskId().equals(taskId));
        if (!isAssigned) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Only the staff member assigned to this task can change its status."));
        }

        Task updated = taskService.updateTaskStatus(taskId, body.get("status"));
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long taskId, Authentication authentication) {
        // An HOD may delete only tasks that belong to their own department
        if (hasRole(authentication, "HEAD_OF_DEPARTMENT")) {
            boolean ownDepartment = taskService.getTasksForMyDepartment(authentication.getName()).stream()
                    .anyMatch(t -> t.getTaskId().equals(taskId));
            if (!ownDepartment) {
                return ResponseEntity.status(403).build();
            }
        }
        if (taskService.deleteTask(taskId)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    private Map<String, Object> toResponse(Task task) {
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
                    Employee emp = a.getEmployee();
                    Map<String, Object> empMap = new HashMap<>();
                    empMap.put("empId", emp.getEmpId());
                    empMap.put("name", emp.getName());
                    empMap.put("employeeCode", emp.getEmployeeCode());
                    return empMap;
                })
                .collect(Collectors.toList());

        map.put("assignedEmployees", assignees);
        return map;
    }

    // ---- Request DTO ----
    public static class TaskCreateRequest {
        private String title;
        private String description;
        private String priority;
        private String dueDate;
        private List<Long> empIds;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public String getDueDate() { return dueDate; }
        public void setDueDate(String dueDate) { this.dueDate = dueDate; }
        public List<Long> getEmpIds() { return empIds; }
        public void setEmpIds(List<Long> empIds) { this.empIds = empIds; }
    }
}