package com.staffportal.staff_management_portal.service;

import com.staffportal.staff_management_portal.model.Department;
import com.staffportal.staff_management_portal.model.Employee;
import com.staffportal.staff_management_portal.model.Role;
import com.staffportal.staff_management_portal.model.User;
import com.staffportal.staff_management_portal.repository.EmployeeRepository;
import com.staffportal.staff_management_portal.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.staffportal.staff_management_portal.repository.TaskAssignmentRepository;
import com.staffportal.staff_management_portal.repository.AttendanceRepository;
import com.staffportal.staff_management_portal.repository.LeaveRequestRepository;
import com.staffportal.staff_management_portal.repository.PayrollRepository;

import java.security.SecureRandom;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EmployeeService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TaskAssignmentRepository taskAssignmentRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private PayrollRepository payrollRepository;

    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    public Employee addEmployee(Employee employee, Role role, String faceDescriptor) {
        employee.setEmployeeCode(generateEmployeeCode());
        Employee savedEmployee = employeeRepository.save(employee);
        createUserAccountForEmployee(savedEmployee, role, faceDescriptor);
        return savedEmployee;
    }

    private String generateEmployeeCode() {
        int year = Year.now().getValue();
        String prefix = "EMP-" + year + "-";

        int maxSuffix = employeeRepository.findAll().stream()
                .map(Employee::getEmployeeCode)
                .filter(code -> code != null && code.startsWith(prefix))
                .map(code -> code.substring(prefix.length()))
                .mapToInt(suffix -> {
                    try {
                        return Integer.parseInt(suffix);
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .max()
                .orElse(0);

        return String.format("%s%04d", prefix, maxSuffix + 1);
    }
    private void createUserAccountForEmployee(Employee employee, Role role, String faceDescriptor) {
        String username = generateUsername(employee.getEmployeeCode());
        String tempPassword = generateTempPassword();

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(tempPassword));
        user.setRole(role != null ? role : Role.FACULTY_MEMBER);
        user.setEmployee(employee);
        user.setMustChangePassword(true);
        if (faceDescriptor != null && !faceDescriptor.isBlank()) {
            user.setFaceDescriptor(faceDescriptor);
        }

        userRepository.save(user);

        employee.setTempPasswordForResponse(tempPassword);
        employee.setGeneratedUsernameForResponse(username);
    }

    private String generateUsername(String employeeCode) {
        return employeeCode.toLowerCase().replace("-", "");
    }

    private String generateTempPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    public Optional<Employee> getEmployeeById(Long empId) {
        return employeeRepository.findById(empId);
    }

    public Employee updateEmployee(Long empId, Employee updatedEmployee) {
        return employeeRepository.findById(empId).map(employee -> {
            employee.setName(updatedEmployee.getName());
            employee.setEmail(updatedEmployee.getEmail());
            employee.setDepartment(updatedEmployee.getDepartment());
            employee.setSalaryPerAnnum(updatedEmployee.getSalaryPerAnnum());
            employee.setDesignation(updatedEmployee.getDesignation());
            return employeeRepository.save(employee);
        }).orElseThrow(() -> new RuntimeException("Employee not found with id: " + empId));
    }

    @org.springframework.transaction.annotation.Transactional
    public boolean deleteEmployee(Long empId) {
        if (!employeeRepository.existsById(empId)) {
            return false;
        }

        // Remove every record that references this employee before deleting them
        taskAssignmentRepository.deleteByEmployee_EmpId(empId);
        attendanceRepository.deleteByEmpId(empId);
        leaveRequestRepository.deleteByEmpId(empId);
        payrollRepository.deleteByEmpId(empId);
        userRepository.findByEmployee_EmpId(empId).ifPresent(userRepository::delete);

        employeeRepository.deleteById(empId);
        return true;
    }
    // ---- Department scoping (for HOD portal) ----

    public List<Employee> getEmployeesByDepartment(Department department) {
        return employeeRepository.findByDepartment(department);
    }

    public Department getDepartmentForUsername(String username) {
        return userRepository.findByUsername(username)
                .map(user -> {
                    if (user.getEmployee() == null) {
                        throw new RuntimeException("This account is not linked to an employee record.");
                    }
                    return user.getEmployee().getDepartment();
                })
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    // ---- Self lookup (for Staff portal) ----

    public Employee getMyEmployeeRecord(String username) {
        return userRepository.findByUsername(username)
                .map(user -> {
                    if (user.getEmployee() == null) {
                        throw new RuntimeException("This account is not linked to an employee record.");
                    }
                    return user.getEmployee();
                })
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }
    public List<java.util.Map<String, Object>> getAllEmployeesWithRole() {
        List<Employee> employees = employeeRepository.findAll();
        return employees.stream().map(emp -> {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("empId", emp.getEmpId());
            map.put("employeeCode", emp.getEmployeeCode());
            map.put("name", emp.getName());
            map.put("email", emp.getEmail());
            map.put("department", emp.getDepartment());
            map.put("salaryPerAnnum", emp.getSalaryPerAnnum());
            map.put("monthlySalary", emp.getMonthlySalary());
            map.put("designation", emp.getDesignation());
            String role = userRepository.findByEmployee_EmpId(emp.getEmpId())
                    .map(u -> u.getRole().name())
                    .orElse(null);
            map.put("role", role);
            return map;
        }).collect(Collectors.toList());
    }

    public void enrollFace(Long empId, String faceDescriptor) {
        User user = userRepository.findByEmployee_EmpId(empId)
                .orElseThrow(() -> new RuntimeException("No user account linked to this employee."));
        user.setFaceDescriptor(faceDescriptor);
        userRepository.save(user);
    }
}