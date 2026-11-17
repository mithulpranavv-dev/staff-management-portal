package com.staffportal.staff_management_portal.service;

import com.staffportal.staff_management_portal.model.LeaveRequest;
import com.staffportal.staff_management_portal.model.User;
import com.staffportal.staff_management_portal.repository.LeaveRequestRepository;
import com.staffportal.staff_management_portal.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Service
public class LeaveBalanceService {

    public static final int PAID_QUOTA_DAYS = 24;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private UserRepository userRepository;

    // All approved leave days in the given year, oldest first, Sundays skipped.
    public List<LocalDate> getApprovedLeaveDays(Long empId, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        TreeSet<LocalDate> days = new TreeSet<>();

        for (LeaveRequest leave : leaveRequestRepository.findByEmpId(empId)) {
            if (!"Approved".equalsIgnoreCase(leave.getStatus())) {
                continue;
            }
            LocalDate day = leave.getFromDate().isBefore(yearStart) ? yearStart : leave.getFromDate();
            LocalDate end = leave.getToDate().isAfter(yearEnd) ? yearEnd : leave.getToDate();
            while (!day.isAfter(end)) {
                if (day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    days.add(day);
                }
                day = day.plusDays(1);
            }
        }
        return new ArrayList<>(days);
    }

    // Days beyond the paid quota (these are the unpaid ones).
    public List<LocalDate> getUnpaidLeaveDays(Long empId, int year) {
        List<LocalDate> all = getApprovedLeaveDays(empId, year);
        if (all.size() <= PAID_QUOTA_DAYS) {
            return new ArrayList<>();
        }
        return new ArrayList<>(all.subList(PAID_QUOTA_DAYS, all.size()));
    }

    public Map<String, Object> getBalanceForEmployee(Long empId, int year) {
        int approvedDays = getApprovedLeaveDays(empId, year).size();
        Map<String, Object> result = new HashMap<>();
        result.put("year", year);
        result.put("paidQuota", PAID_QUOTA_DAYS);
        result.put("approvedDays", approvedDays);
        result.put("paidUsed", Math.min(approvedDays, PAID_QUOTA_DAYS));
        result.put("paidRemaining", Math.max(0, PAID_QUOTA_DAYS - approvedDays));
        result.put("unpaidDays", Math.max(0, approvedDays - PAID_QUOTA_DAYS));
        return result;
    }

    public Map<String, Object> getMyBalance(String username, int year) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        if (user.getEmployee() == null) {
            throw new RuntimeException("This account is not linked to an employee record.");
        }
        return getBalanceForEmployee(user.getEmployee().getEmpId(), year);
    }
}