package com.staffportal.staff_management_portal.service;

import com.staffportal.staff_management_portal.model.Department;
import com.staffportal.staff_management_portal.model.Employee;
import com.staffportal.staff_management_portal.model.LeaveRequest;
import com.staffportal.staff_management_portal.model.User;
import com.staffportal.staff_management_portal.repository.EmployeeRepository;
import com.staffportal.staff_management_portal.repository.LeaveRequestRepository;
import com.staffportal.staff_management_portal.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LeaveRequestService {

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    private Long resolveMyEmpId(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        if (user.getEmployee() == null) {
            throw new RuntimeException("This account is not linked to an employee record.");
        }
        return user.getEmployee().getEmpId();
    }

    // The employee is taken from the login token, never from the request body.
    public LeaveRequest applyLeave(String username, LeaveRequest leaveRequest) {
        Long myEmpId = resolveMyEmpId(username);

        if (leaveRequest.getFromDate() == null || leaveRequest.getToDate() == null) {
            throw new RuntimeException("From date and To date are required.");
        }
        if (leaveRequest.getToDate().isBefore(leaveRequest.getFromDate())) {
            throw new RuntimeException("To date cannot be before From date.");
        }
        if (leaveRequest.getLeaveType() == null || leaveRequest.getLeaveType().isBlank()) {
            throw new RuntimeException("Leave type is required.");
        }

        // Overlap check against this employee's existing, non-rejected leaves
        for (LeaveRequest existing : leaveRequestRepository.findByEmpId(myEmpId)) {
            if ("Rejected".equalsIgnoreCase(existing.getStatus())) {
                continue;
            }
            boolean overlaps = !leaveRequest.getFromDate().isAfter(existing.getToDate())
                    && !leaveRequest.getToDate().isBefore(existing.getFromDate());
            if (overlaps) {
                throw new RuntimeException("These dates overlap with an existing leave request ("
                        + existing.getFromDate() + " to " + existing.getToDate() + ").");
            }
        }

        leaveRequest.setLeaveId(null);
        leaveRequest.setEmpId(myEmpId);
        leaveRequest.setStatus("Pending");
        leaveRequest.setRejectionReason(null);
        return leaveRequestRepository.save(leaveRequest);
    }

    public List<LeaveRequest> getMyLeaves(String username) {
        return leaveRequestRepository.findByEmpId(resolveMyEmpId(username));
    }

    public List<LeaveRequest> getAllLeaveRequests() {
        return leaveRequestRepository.findAll();
    }

    public List<LeaveRequest> getLeaveRequestsByEmployee(Long empId) {
        return leaveRequestRepository.findByEmpId(empId);
    }

    public LeaveRequest updateLeaveStatus(Long leaveId, String status, String rejectionReason) {
        return leaveRequestRepository.findById(leaveId).map(leave -> {
            leave.setStatus(status);
            if ("Rejected".equalsIgnoreCase(status)) {
                leave.setRejectionReason(rejectionReason);
            } else {
                leave.setRejectionReason(null);
            }
            return leaveRequestRepository.save(leave);
        }).orElse(null);
    }

    public boolean deleteLeaveRequest(Long leaveId) {
        if (leaveRequestRepository.existsById(leaveId)) {
            leaveRequestRepository.deleteById(leaveId);
            return true;
        }
        return false;
    }

    // ---- Department scoping (for HOD portal, view-only) ----

    public List<LeaveRequest> getLeaveRequestsForMyDepartment(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        if (user.getEmployee() == null) {
            throw new RuntimeException("This account is not linked to an employee record.");
        }

        Department department = user.getEmployee().getDepartment();

        List<Long> deptEmpIds = employeeRepository.findByDepartment(department).stream()
                .map(Employee::getEmpId)
                .collect(Collectors.toList());

        return leaveRequestRepository.findByEmpIdIn(deptEmpIds);
    }
}