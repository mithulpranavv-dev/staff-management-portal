package com.staffportal.staff_management_portal.service;

import com.staffportal.staff_management_portal.model.*;
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
public class ChatbotContextService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private LeaveRequestService leaveRequestService;

    @Autowired
    private LeaveBalanceService leaveBalanceService;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private PerformanceService performanceService;

    public String buildContext(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        Role role = user.getRole();
        StringBuilder ctx = new StringBuilder();

        ctx.append("Logged-in user: ").append(username)
                .append(" | Role: ").append(role)
                .append("\n\n");

        switch (role) {
            case FACULTY_MEMBER -> ctx.append(buildStaffContext(username));
            case HEAD_OF_DEPARTMENT -> ctx.append(buildHodContext(username));
            case DEAN -> ctx.append(buildDeanOrAdminContext(true));
            case ADMINISTRATOR -> ctx.append(buildDeanOrAdminContext(false));
            default -> ctx.append("No additional portal data available for this role.");
        }

        return ctx.toString();
    }

    // ---- Staff: personal data only ----
    private String buildStaffContext(String username) {
        Employee me = employeeService.getMyEmployeeRecord(username);
        StringBuilder sb = new StringBuilder();

        sb.append("This user's own employee record: ")
                .append(me.getName()).append(" (").append(me.getEmployeeCode()).append("), ")
                .append("Department: ").append(me.getDepartment()).append("\n\n");

        // Paid leave balance (24 paid days per year, approved leave only, Sundays not counted)
        int year = LocalDate.now().getYear();
        Map<String, Object> bal = leaveBalanceService.getBalanceForEmployee(me.getEmpId(), year);
        sb.append("My paid leave balance for ").append(year).append(": ")
                .append(bal.get("paidUsed")).append(" of ").append(bal.get("paidQuota")).append(" paid days used, ")
                .append(bal.get("paidRemaining")).append(" paid days remaining, ")
                .append(bal.get("unpaidDays")).append(" unpaid days. ")
                .append("Rules: every employee gets ").append(bal.get("paidQuota"))
                .append(" paid leave days per calendar year; only APPROVED leave counts (pending and rejected do not); ")
                .append("Sundays are not counted; each approved day beyond the quota is unpaid and one full day's salary is deducted in payroll.\n\n");

        List<LeaveRequest> myLeaves = leaveRequestService.getLeaveRequestsByEmployee(me.getEmpId());
        sb.append("My leave requests (").append(myLeaves.size()).append(" total):\n");
        for (LeaveRequest lr : myLeaves) {
            sb.append("- #").append(lr.getLeaveId()).append(" ").append(lr.getLeaveType())
                    .append(" (").append(lr.getFromDate()).append(" to ").append(lr.getToDate()).append("): ")
                    .append(lr.getStatus());
            if ("Rejected".equalsIgnoreCase(lr.getStatus()) && lr.getRejectionReason() != null) {
                sb.append(" — Reason: ").append(lr.getRejectionReason());
            }
            sb.append("\n");
        }

        List<Task> myTasks = taskService.getMyTasks(username);
        sb.append("\nMy assigned tasks (").append(myTasks.size()).append(" total):\n");
        for (Task t : myTasks) {
            sb.append("- #").append(t.getTaskId()).append(" \"").append(t.getTitle())
                    .append("\" [").append(t.getPriority()).append("] Due: ").append(t.getDueDate())
                    .append(" Status: ").append(t.getStatus()).append("\n");
        }

        List<Attendance> myAttendance = attendanceService.getAttendanceByEmployee(me.getEmpId());
        sb.append("\nMy recent attendance (").append(myAttendance.size()).append(" records total)\n");

        List<Payroll> myPayroll = payrollService.getMyPayroll(username);
        sb.append("\nMy payroll records (").append(myPayroll.size()).append(" total):\n");
        for (Payroll p : myPayroll) {
            sb.append("- ").append(p.getMonth()).append(": Net Salary ₹").append(p.getNetSalary())
                    .append(" (Deductions: ₹").append(p.getDeductions()).append(")\n");
        }

        return sb.toString();
    }

    // ---- HOD: own department only, aggregate + own personal data ----
    @SuppressWarnings("unchecked")
    private String buildHodContext(String username) {
        StringBuilder sb = new StringBuilder();

        // HOD also has their own personal data as a staff member
        sb.append(buildStaffContext(username));

        Department dept = employeeService.getDepartmentForUsername(username);
        sb.append("\n--- Department-level data (").append(dept).append(") ---\n");

        List<Employee> deptStaff = employeeService.getEmployeesByDepartment(dept);
        sb.append("Department staff count: ").append(deptStaff.size()).append("\n");

        List<LeaveRequest> deptLeaves = leaveRequestService.getLeaveRequestsForMyDepartment(username);
        Map<String, Long> leaveStatusCounts = deptLeaves.stream()
                .collect(Collectors.groupingBy(LeaveRequest::getStatus, Collectors.counting()));
        sb.append("Department leave requests: ").append(deptLeaves.size())
                .append(" total — breakdown: ").append(leaveStatusCounts).append("\n");

        List<Attendance> deptAttendance = attendanceService.getAttendanceForMyDepartment(username);
        sb.append("Department attendance records: ").append(deptAttendance.size()).append(" total\n");

        try {
            Map<String, Object> perf = performanceService.forMyDepartment(username);
            List<Map<String, Object>> people = (List<Map<String, Object>>) perf.get("people");
            sb.append("\n--- Staff performance in my department ---\n");
            sb.append(buildPerformanceLines(people));
        } catch (RuntimeException e) {
            sb.append("\n(Staff performance data is not available.)\n");
        }

        return sb.toString();
    }

    // ---- Dean/Administrator: full visibility across everyone ----
    @SuppressWarnings("unchecked")
    private String buildDeanOrAdminContext(boolean includePerformance) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- Organization-wide data (full access) ---\n");

        List<Employee> allEmployees = employeeService.getAllEmployees();
        sb.append("Total employees: ").append(allEmployees.size()).append("\n");

        List<LeaveRequest> allLeaves = leaveRequestService.getAllLeaveRequests();
        Map<String, Long> leaveStatusCounts = allLeaves.stream()
                .collect(Collectors.groupingBy(LeaveRequest::getStatus, Collectors.counting()));
        sb.append("Total leave requests: ").append(allLeaves.size())
                .append(" — breakdown: ").append(leaveStatusCounts).append("\n");

        List<Attendance> allAttendance = attendanceService.getAllAttendance();
        sb.append("Total attendance records: ").append(allAttendance.size()).append("\n");

        List<Task> allTasks = taskService.getAllTasks();
        Map<String, Long> taskStatusCounts = allTasks.stream()
                .collect(Collectors.groupingBy(Task::getStatus, Collectors.counting()));
        sb.append("Total tasks: ").append(allTasks.size())
                .append(" — breakdown: ").append(taskStatusCounts).append("\n");

        if (includePerformance) {
            Map<String, Object> perf = performanceService.forAll();
            List<Map<String, Object>> people = (List<Map<String, Object>>) perf.get("people");
            sb.append("\n--- Staff performance (per person) ---\n");
            sb.append(buildPerformanceLines(people));
        }

        return sb.toString();
    }

    // One block per person: task titles with status, plus the rates.
    private String buildPerformanceLines(List<Map<String, Object>> people) {
        Map<Long, List<String>> titlesByEmp = new HashMap<>();
        for (Task t : taskService.getAllTasks()) {
            for (TaskAssignment a : t.getAssignments()) {
                if (a.getEmployee() == null) continue;
                titlesByEmp.computeIfAbsent(a.getEmployee().getEmpId(), k -> new ArrayList<>())
                        .add("\"" + t.getTitle() + "\" (" + t.getStatus() + ", due " + t.getDueDate() + ")");
            }
        }

        StringBuilder sb = new StringBuilder();
        if (people == null || people.isEmpty()) {
            return "No staff to report on.\n";
        }
        for (Map<String, Object> p : people) {
            long empId = ((Number) p.get("empId")).longValue();
            sb.append("- ").append(p.get("name")).append(" (").append(p.get("employeeCode"))
                    .append(", ").append(p.get("department")).append("): ")
                    .append(p.get("assigned")).append(" tasks assigned, ")
                    .append(p.get("completed")).append(" completed (")
                    .append(p.get("onTime")).append(" on time, ").append(p.get("late")).append(" late), ")
                    .append(p.get("inProgress")).append(" in progress, ")
                    .append(p.get("pending")).append(" pending. ")
                    .append("Completion rate ").append(rate(p.get("completionRate")))
                    .append(", on-time rate ").append(rate(p.get("onTimeRate")))
                    .append(", attendance rate (last 30 days) ").append(rate(p.get("attendanceRate")))
                    .append(".\n");
            List<String> titles = titlesByEmp.get(empId);
            if (titles != null) {
                for (String title : titles) {
                    sb.append("    task: ").append(title).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private String rate(Object value) {
        return value == null ? "n/a" : value + "%";
    }
}