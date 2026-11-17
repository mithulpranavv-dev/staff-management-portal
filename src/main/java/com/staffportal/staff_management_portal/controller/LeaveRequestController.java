package com.staffportal.staff_management_portal.controller;

import com.staffportal.staff_management_portal.model.LeaveRequest;
import com.staffportal.staff_management_portal.service.LeaveBalanceService;
import com.staffportal.staff_management_portal.service.LeaveInsightsService;
import com.staffportal.staff_management_portal.service.LeaveRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leaves")
public class LeaveRequestController {

    @Autowired
    private LeaveRequestService leaveRequestService;

    @Autowired
    private LeaveInsightsService leaveInsightsService;

    @Autowired
    private LeaveBalanceService leaveBalanceService;

    @PostMapping
    public ResponseEntity<?> applyLeave(Authentication authentication, @RequestBody LeaveRequest leaveRequest) {
        try {
            return ResponseEntity.ok(leaveRequestService.applyLeave(authentication.getName(), leaveRequest));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public List<LeaveRequest> getAllLeaveRequests() {
        return leaveRequestService.getAllLeaveRequests();
    }

    @GetMapping("/me")
    public List<LeaveRequest> getMyLeaves(Authentication authentication) {
        return leaveRequestService.getMyLeaves(authentication.getName());
    }

    @GetMapping("/balance")
    public ResponseEntity<?> getMyBalance(Authentication authentication,
                                          @RequestParam(required = false) Integer year) {
        try {
            int y = (year != null) ? year : LocalDate.now().getYear();
            return ResponseEntity.ok(leaveBalanceService.getMyBalance(authentication.getName(), y));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/my-department")
    public List<LeaveRequest> getMyDepartmentLeaveRequests(Authentication authentication) {
        String username = authentication.getName();
        return leaveRequestService.getLeaveRequestsForMyDepartment(username);
    }

    // ---- Leave Insights ----

    @GetMapping("/insights/me")
    public Map<String, String> getMyInsights(Authentication authentication) {
        String insights = leaveInsightsService.getMyInsights(authentication.getName());
        return Map.of("insights", insights);
    }

    @GetMapping("/insights/my-department")
    public Map<String, String> getMyDepartmentInsights(Authentication authentication) {
        String insights = leaveInsightsService.getMyDepartmentInsights(authentication.getName());
        return Map.of("insights", insights);
    }

    @GetMapping("/insights/all")
    public Map<String, String> getOrgWideInsights() {
        String insights = leaveInsightsService.getOrgWideInsights();
        return Map.of("insights", insights);
    }

    @GetMapping("/employee/{empId}")
    public List<LeaveRequest> getLeaveRequestsByEmployee(@PathVariable Long empId) {
        return leaveRequestService.getLeaveRequestsByEmployee(empId);
    }

    @PutMapping("/{leaveId}/status")
    public LeaveRequest updateLeaveStatus(@PathVariable Long leaveId, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        String rejectionReason = body.get("rejectionReason");
        return leaveRequestService.updateLeaveStatus(leaveId, status, rejectionReason);
    }

    @DeleteMapping("/{leaveId}")
    public String deleteLeaveRequest(@PathVariable Long leaveId) {
        boolean deleted = leaveRequestService.deleteLeaveRequest(leaveId);
        return deleted ? "Leave request deleted successfully." : "Leave request not found.";
    }
}