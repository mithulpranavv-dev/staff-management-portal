package com.staffportal.staff_management_portal.service;

import com.staffportal.staff_management_portal.model.*;
import com.staffportal.staff_management_portal.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SmartSearchService {

    private static final int MAX_QUERY_LENGTH = 200;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private LeaveRequestService leaveRequestService;

    @Autowired
    private GeminiService geminiService;

    // Only the Dean and Administrator may see salary figures.
    public boolean canSeeSalary(String username) {
        Role role = getRole(username);
        return role == Role.DEAN || role == Role.ADMINISTRATOR;
    }

    // ---- Employees ----

    public List<Employee> searchEmployees(String username, String query) {
        String cleaned = cleanQuery(query);
        if (cleaned.isEmpty()) return List.of();

        boolean showSalary = canSeeSalary(username);
        List<Employee> scoped = getScopedEmployees(username);
        StringBuilder summary = new StringBuilder();
        for (Employee e : scoped) {
            String role = userRepository.findByEmployee_EmpId(e.getEmpId())
                    .map(u -> u.getRole().toString())
                    .orElse("N/A");

            summary.append(e.getEmpId()).append(": ").append(e.getName())
                    .append(", Dept: ").append(e.getDepartment() != null ? e.getDepartment() : "N/A")
                    .append(", Code: ").append(e.getEmployeeCode())
                    .append(", Role: ").append(role)
                    .append(", Designation: ").append(e.getDesignation() != null ? e.getDesignation() : "N/A");
            if (showSalary) {
                summary.append(", Salary/Annum: ").append(e.getSalaryPerAnnum() != null ? e.getSalaryPerAnnum() : "N/A");
            }
            summary.append("\n");
        }

        List<Long> matchedIds = askGeminiForMatchingIds(summary.toString(), cleaned);
        return scoped.stream().filter(e -> matchedIds.contains(e.getEmpId())).collect(Collectors.toList());
    }

    private List<Employee> getScopedEmployees(String username) {
        Role role = getRole(username);
        return switch (role) {
            case HEAD_OF_DEPARTMENT -> employeeService.getEmployeesByDepartment(employeeService.getDepartmentForUsername(username));
            case DEAN, ADMINISTRATOR -> employeeService.getAllEmployees();
            default -> List.of(employeeService.getMyEmployeeRecord(username));
        };
    }

    // ---- Tasks ----

    public List<Task> searchTasks(String username, String query) {
        String cleaned = cleanQuery(query);
        if (cleaned.isEmpty()) return List.of();

        List<Task> scoped = getScopedTasks(username);
        if (scoped.isEmpty()) return List.of();

        StringBuilder summary = new StringBuilder();
        for (Task t : scoped) {
            summary.append(t.getTaskId()).append(": \"").append(t.getTitle()).append("\"")
                    .append(", Priority: ").append(t.getPriority())
                    .append(", Due: ").append(t.getDueDate())
                    .append(", Status: ").append(t.getStatus())
                    .append("\n");
        }

        List<Long> matchedIds = askGeminiForMatchingIds(summary.toString(), cleaned);
        return scoped.stream().filter(t -> matchedIds.contains(t.getTaskId())).collect(Collectors.toList());
    }

    private List<Task> getScopedTasks(String username) {
        Role role = getRole(username);
        return switch (role) {
            case DEAN -> taskService.getAllTasks();
            case ADMINISTRATOR -> List.of(); // the Administrator has no access to tasks
            case HEAD_OF_DEPARTMENT -> taskService.getTasksForMyDepartment(username);
            default -> taskService.getMyTasks(username);
        };
    }

    // ---- Leaves ----

    public List<LeaveRequest> searchLeaves(String username, String query) {
        String cleaned = cleanQuery(query);
        if (cleaned.isEmpty()) return List.of();

        List<LeaveRequest> scoped = getScopedLeaves(username);
        StringBuilder summary = new StringBuilder();
        for (LeaveRequest l : scoped) {
            summary.append(l.getLeaveId()).append(": EmpId ").append(l.getEmpId())
                    .append(", Type: ").append(l.getLeaveType())
                    .append(", ").append(l.getFromDate()).append(" to ").append(l.getToDate())
                    .append(", Status: ").append(l.getStatus())
                    .append("\n");
        }

        List<Long> matchedIds = askGeminiForMatchingIds(summary.toString(), cleaned);
        return scoped.stream().filter(l -> matchedIds.contains(l.getLeaveId())).collect(Collectors.toList());
    }

    private List<LeaveRequest> getScopedLeaves(String username) {
        Role role = getRole(username);
        return switch (role) {
            case DEAN, ADMINISTRATOR -> leaveRequestService.getAllLeaveRequests();
            case HEAD_OF_DEPARTMENT -> leaveRequestService.getLeaveRequestsForMyDepartment(username);
            default -> leaveRequestService.getLeaveRequestsByEmployee(employeeService.getMyEmployeeRecord(username).getEmpId());
        };
    }

    // ---- Shared Gemini call ----

    // Removes line breaks and quote marks, and limits the length of the search text.
    private String cleanQuery(String query) {
        if (query == null) return "";
        String q = query.replaceAll("[\\r\\n\"]", " ").trim();
        if (q.length() > MAX_QUERY_LENGTH) {
            q = q.substring(0, MAX_QUERY_LENGTH);
        }
        return q;
    }

    private List<Long> askGeminiForMatchingIds(String dataSummary, String query) {
        String prompt = """
                You are a search filter. Below is a list of records, each starting with its ID number.

                RECORDS:
                %s

                USER QUERY: "%s"

                Return ONLY a JSON array of the ID numbers that match the query, with no other text.
                Example: [3, 7, 12]
                If nothing matches, return: []
                """.formatted(dataSummary, query);

        String reply = geminiService.ask(prompt);
        return parseIdArray(reply);
    }

    private List<Long> parseIdArray(String text) {
        List<Long> ids = new ArrayList<>();
        try {
            Pattern arrayPattern = Pattern.compile("\\[[^\\]]*]");
            Matcher arrayMatcher = arrayPattern.matcher(text);
            if (arrayMatcher.find()) {
                String jsonArray = arrayMatcher.group();
                Pattern numberPattern = Pattern.compile("\\d+");
                Matcher numberMatcher = numberPattern.matcher(jsonArray);
                while (numberMatcher.find()) {
                    ids.add(Long.parseLong(numberMatcher.group()));
                }
            }
        } catch (Exception e) {
            // If parsing fails, return no matches rather than crashing the request
        }
        return ids;
    }

    private Role getRole(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        return user.getRole();
    }
}