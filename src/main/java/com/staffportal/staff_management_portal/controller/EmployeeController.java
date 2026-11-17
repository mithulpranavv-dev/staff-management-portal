package com.staffportal.staff_management_portal.controller;

import com.staffportal.staff_management_portal.model.Department;
import com.staffportal.staff_management_portal.model.Employee;
import com.staffportal.staff_management_portal.model.Role;
import com.staffportal.staff_management_portal.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    // Dean and Administrator see everything. HOD sees email but no salary. Others see neither.
    private Map<String, Object> toSafeMap(Employee e, Authentication auth) {
        boolean fullAccess = hasRole(auth, "DEAN") || hasRole(auth, "ADMINISTRATOR");
        boolean seesEmail = fullAccess || hasRole(auth, "HEAD_OF_DEPARTMENT");

        Map<String, Object> map = new HashMap<>();
        map.put("empId", e.getEmpId());
        map.put("name", e.getName());
        map.put("employeeCode", e.getEmployeeCode());
        map.put("department", e.getDepartment());
        map.put("designation", e.getDesignation());
        if (seesEmail) {
            map.put("email", e.getEmail());
        }
        if (fullAccess) {
            map.put("salaryPerAnnum", e.getSalaryPerAnnum());
            map.put("monthlySalary", e.getMonthlySalary());
        }
        return map;
    }

    @PostMapping
    public Employee addEmployee(@RequestBody EmployeeCreateRequest request) {
        Employee employee = new Employee();
        employee.setName(request.getName());
        employee.setEmail(request.getEmail());
        employee.setDepartment(request.getDepartment());
        employee.setSalaryPerAnnum(request.getSalaryPerAnnum());
        employee.setDesignation(request.getDesignation());

        return employeeService.addEmployee(employee, request.getRole(), request.getFaceDescriptor());
    }

    @GetMapping
    public List<Map<String, Object>> getAllEmployees() {
        return employeeService.getAllEmployeesWithRole();
    }

    @GetMapping("/my-department")
    public List<Map<String, Object>> getMyDepartmentEmployees(Authentication authentication) {
        // Dean and Administrator have no department. They use GET /api/employees instead.
        if (hasRole(authentication, "DEAN") || hasRole(authentication, "ADMINISTRATOR")) {
            return List.of();
        }
        String username = authentication.getName();
        Department department = employeeService.getDepartmentForUsername(username);
        if (department == null) {
            return List.of();
        }
        return employeeService.getEmployeesByDepartment(department).stream()
                .map(e -> toSafeMap(e, authentication))
                .collect(Collectors.toList());
    }

    @GetMapping("/department/{department}")
    public List<Map<String, Object>> getEmployeesByDepartment(@PathVariable Department department,
                                                              Authentication authentication) {
        return employeeService.getEmployeesByDepartment(department).stream()
                .map(e -> toSafeMap(e, authentication))
                .collect(Collectors.toList());
    }

    @GetMapping("/me")
    public Employee getMyEmployeeRecord(Authentication authentication) {
        String username = authentication.getName();
        return employeeService.getMyEmployeeRecord(username);
    }

    @GetMapping("/{empId}")
    public Optional<Employee> getEmployeeById(@PathVariable Long empId) {
        return employeeService.getEmployeeById(empId);
    }

    @PutMapping("/{empId}")
    public Employee updateEmployee(@PathVariable Long empId, @RequestBody Employee employee) {
        return employeeService.updateEmployee(empId, employee);
    }

    @DeleteMapping("/{empId}")
    public String deleteEmployee(@PathVariable Long empId) {
        boolean deleted = employeeService.deleteEmployee(empId);
        return deleted ? "Employee deleted successfully." : "Employee not found.";
    }

    // ---- Request DTO ----
    public static class EmployeeCreateRequest {
        private String name;
        private String email;
        private Department department;
        private Role role;
        private Double salaryPerAnnum;
        private String faceDescriptor;
        private String designation;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public Department getDepartment() { return department; }
        public void setDepartment(Department department) { this.department = department; }
        public Role getRole() { return role; }
        public void setRole(Role role) { this.role = role; }
        public Double getSalaryPerAnnum() { return salaryPerAnnum; }
        public void setSalaryPerAnnum(Double salaryPerAnnum) { this.salaryPerAnnum = salaryPerAnnum; }
        public String getFaceDescriptor() { return faceDescriptor; }
        public void setFaceDescriptor(String faceDescriptor) { this.faceDescriptor = faceDescriptor; }
        public String getDesignation() { return designation; }
        public void setDesignation(String designation) { this.designation = designation; }
    }

    @PostMapping("/{empId}/enroll-face")
    public ResponseEntity<?> enrollFace(@PathVariable Long empId, @RequestBody Map<String, String> body) {
        try {
            employeeService.enrollFace(empId, body.get("faceDescriptor"));
            return ResponseEntity.ok(Map.of("message", "Face enrolled successfully."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}