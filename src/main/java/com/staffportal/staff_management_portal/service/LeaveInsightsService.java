package com.staffportal.staff_management_portal.service;

import com.staffportal.staff_management_portal.model.Department;
import com.staffportal.staff_management_portal.model.Employee;
import com.staffportal.staff_management_portal.model.LeaveRequest;
import com.staffportal.staff_management_portal.model.User;
import com.staffportal.staff_management_portal.repository.EmployeeRepository;
import com.staffportal.staff_management_portal.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LeaveInsightsService {

    @Autowired
    private LeaveRequestService leaveRequestService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GeminiService geminiService;

    public String getMyInsights(String username) {
        Employee me = getMyEmployee(username);
        List<LeaveRequest> myLeaves = leaveRequestService.getLeaveRequestsByEmployee(me.getEmpId());
        String summary = buildSummary(myLeaves, "the employee's own");
        return askGeminiForInsights(summary);
    }

    public String getMyDepartmentInsights(String username) {
        Department dept = getMyEmployee(username).getDepartment();
        List<LeaveRequest> deptLeaves = leaveRequestService.getLeaveRequestsForMyDepartment(username);
        String summary = buildSummary(deptLeaves, "the " + dept + " department's");
        return askGeminiForInsights(summary);
    }

    public String getOrgWideInsights() {
        List<LeaveRequest> allLeaves = leaveRequestService.getAllLeaveRequests();
        String summary = buildOrgWideSummary(allLeaves);
        return askGeminiForInsights(summary);
    }

    // ---- Summary building ----

    private String buildSummary(List<LeaveRequest> leaves, String scopeLabel) {
        if (leaves.isEmpty()) {
            return "No leave requests found for " + scopeLabel + " records.";
        }

        Map<String, Long> byType = leaves.stream()
                .collect(Collectors.groupingBy(LeaveRequest::getLeaveType, Collectors.counting()));
        Map<String, Long> byStatus = leaves.stream()
                .collect(Collectors.groupingBy(LeaveRequest::getStatus, Collectors.counting()));
        Map<String, Long> byMonth = leaves.stream()
                .collect(Collectors.groupingBy(
                        lr -> lr.getFromDate().getMonth().toString(),
                        Collectors.counting()));

        StringBuilder sb = new StringBuilder();
        sb.append("Leave data for ").append(scopeLabel).append(" records:\n");
        sb.append("Total requests: ").append(leaves.size()).append("\n");
        sb.append("By leave type: ").append(byType).append("\n");
        sb.append("By status: ").append(byStatus).append("\n");
        sb.append("By month applied: ").append(byMonth).append("\n");

        return sb.toString();
    }

    private String buildOrgWideSummary(List<LeaveRequest> leaves) {
        if (leaves.isEmpty()) {
            return "No leave requests found organization-wide.";
        }

        Map<Long, List<LeaveRequest>> byEmployee = leaves.stream()
                .collect(Collectors.groupingBy(LeaveRequest::getEmpId));

        Map<String, Long> byDepartment = byEmployee.entrySet().stream()
                .collect(Collectors.groupingBy(
                        entry -> employeeRepository.findById(entry.getKey())
                                .map(e -> e.getDepartment() != null ? e.getDepartment().toString() : "N/A")
                                .orElse("Unknown"),
                        Collectors.summingLong(entry -> entry.getValue().size())
                ));

        Map<String, Long> byType = leaves.stream()
                .collect(Collectors.groupingBy(LeaveRequest::getLeaveType, Collectors.counting()));
        Map<String, Long> byStatus = leaves.stream()
                .collect(Collectors.groupingBy(LeaveRequest::getStatus, Collectors.counting()));
        Map<String, Long> byMonth = leaves.stream()
                .collect(Collectors.groupingBy(
                        lr -> lr.getFromDate().getMonth().toString(),
                        Collectors.counting()));

        StringBuilder sb = new StringBuilder();
        sb.append("Organization-wide leave data:\n");
        sb.append("Total requests: ").append(leaves.size()).append("\n");
        sb.append("By department: ").append(byDepartment).append("\n");
        sb.append("By leave type: ").append(byType).append("\n");
        sb.append("By status: ").append(byStatus).append("\n");
        sb.append("By month applied: ").append(byMonth).append("\n");

        return sb.toString();
    }

    // ---- Gemini call ----

    private String askGeminiForInsights(String dataSummary) {
        String prompt = """
                You are analyzing leave request data for a college staff management portal.
                Below is a statistical summary (counts and breakdowns, not raw records).

                DATA:
                %s

                Write a short, clear insights summary (4-6 sentences) highlighting the most
                notable patterns — e.g. which leave type is most common, any month with a
                spike, approval vs rejection rate, or department differences if present.
                Do not invent numbers not shown above. Write in plain, friendly language,
                not bullet points.
                """.formatted(dataSummary);

        return geminiService.ask(prompt);
    }

    // ---- Helper ----

    private Employee getMyEmployee(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        if (user.getEmployee() == null) {
            throw new RuntimeException("This account is not linked to an employee record.");
        }
        return user.getEmployee();
    }
}