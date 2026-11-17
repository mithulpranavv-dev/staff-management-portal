package com.staffportal.staff_management_portal.service;

import com.staffportal.staff_management_portal.model.Attendance;
import com.staffportal.staff_management_portal.model.Department;
import com.staffportal.staff_management_portal.model.Employee;
import com.staffportal.staff_management_portal.model.User;
import com.staffportal.staff_management_portal.repository.AttendanceRepository;
import com.staffportal.staff_management_portal.repository.EmployeeRepository;
import com.staffportal.staff_management_portal.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AttendanceService {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QrTokenService qrTokenService;

    @Autowired
    private LeaveBalanceService leaveBalanceService;

    public Attendance markAttendance(Attendance attendance) {
        if (attendance.getEmpId() == null || !employeeRepository.existsById(attendance.getEmpId())) {
            throw new RuntimeException("Employee not found. Please choose an existing employee.");
        }
        if (attendance.getAttDate() == null) {
            throw new RuntimeException("Attendance date is required.");
        }
        if (attendance.getStatus() == null || attendance.getStatus().isBlank()) {
            throw new RuntimeException("Attendance status is required.");
        }
        if (attendanceRepository.findByEmpIdAndAttDate(attendance.getEmpId(), attendance.getAttDate()).isPresent()) {
            throw new RuntimeException("This employee already has an attendance record for that date. Edit the existing record instead.");
        }
        return attendanceRepository.save(attendance);
    }

    public List<Attendance> getAllAttendance() {
        return attendanceRepository.findAll();
    }

    public List<Attendance> getAttendanceByEmployee(Long empId) {
        return attendanceRepository.findByEmpId(empId);
    }

    public Attendance updateAttendance(Long attId, Attendance updatedAttendance) {
        return attendanceRepository.findById(attId).map(attendance -> {
            attendance.setCheckIn(updatedAttendance.getCheckIn());
            attendance.setCheckOut(updatedAttendance.getCheckOut());
            attendance.setStatus(updatedAttendance.getStatus());
            return attendanceRepository.save(attendance);
        }).orElse(null);
    }

    public boolean deleteAttendance(Long attId) {
        if (attendanceRepository.existsById(attId)) {
            attendanceRepository.deleteById(attId);
            return true;
        }
        return false;
    }

    // TEMPORARY TEST FLAG — set to false before real/demo use. Bypasses time-window
    // restrictions so check-in/checkout can be tested regardless of current time.
    private static final boolean TEST_MODE_BYPASS_TIME_WINDOWS = false;

    public Attendance processScan(Long empId, String scannedToken) {
        if (!qrTokenService.isValid(empId, scannedToken)) {
            throw new RuntimeException("This QR code has expired. Ask the staff member to refresh it.");
        }

        java.time.LocalTime now = java.time.LocalTime.now();

        if (TEST_MODE_BYPASS_TIME_WINDOWS) {
            java.time.LocalDate today = java.time.LocalDate.now();
            boolean alreadyCheckedIn = attendanceRepository.findByEmpIdAndAttDate(empId, today).isPresent();
            return alreadyCheckedIn ? doCheckOut(empId, now) : doCheckIn(empId, now);
        }

        java.time.LocalTime checkInStart = java.time.LocalTime.of(7, 0);
        java.time.LocalTime checkInCutoff = java.time.LocalTime.of(10, 0);
        java.time.LocalTime checkOutStart = java.time.LocalTime.of(16, 0);
        java.time.LocalTime checkOutEnd = java.time.LocalTime.of(18, 0);

        boolean inCheckInWindow = !now.isBefore(checkInStart) && now.isBefore(checkInCutoff);
        boolean inCheckOutWindow = !now.isBefore(checkOutStart) && !now.isAfter(checkOutEnd);

        if (inCheckInWindow) {
            return doCheckIn(empId, now);
        } else if (inCheckOutWindow) {
            return doCheckOut(empId, now);
        } else if (now.isBefore(checkInStart)) {
            throw new RuntimeException("Check-in is not open yet. Please try from 7:00 AM.");
        } else if (!now.isBefore(checkInCutoff) && now.isBefore(checkOutStart)) {
            throw new RuntimeException("Check-in closed at 10:00 AM, and check-out doesn't open until 4:00 PM.");
        } else {
            throw new RuntimeException("Check-out window has closed for today.");
        }
    }

    private Attendance doCheckIn(Long empId, java.time.LocalTime now) {
        java.time.LocalDate today = java.time.LocalDate.now();

        if (attendanceRepository.findByEmpIdAndAttDate(empId, today).isPresent()) {
            throw new RuntimeException("This staff member has already checked in today.");
        }

        String status = determineStatus(empId, today, now);

        Attendance attendance = new Attendance();
        attendance.setEmpId(empId);
        attendance.setAttDate(today);
        attendance.setCheckIn(now);
        attendance.setStatus(status);

        return attendanceRepository.save(attendance);
    }

    private Attendance doCheckOut(Long empId, java.time.LocalTime now) {
        java.time.LocalDate today = java.time.LocalDate.now();

        Attendance attendance = attendanceRepository.findByEmpIdAndAttDate(empId, today)
                .orElseThrow(() -> new RuntimeException("No check-in record found for this staff member today."));

        if (attendance.getCheckOut() != null) {
            throw new RuntimeException("This staff member has already checked out today.");
        }

        attendance.setCheckOut(now);
        return attendanceRepository.save(attendance);
    }

    private String determineStatus(Long empId, java.time.LocalDate today, java.time.LocalTime checkInTime) {
        java.time.LocalTime nineAM = java.time.LocalTime.of(9, 0);
        java.time.LocalTime nineThirtyAM = java.time.LocalTime.of(9, 30);

        if (checkInTime.isBefore(nineAM)) {
            return "Present";
        }

        if (checkInTime.isAfter(nineThirtyAM)) {
            return "Half Day";
        }

        java.time.LocalDate monthStart = today.withDayOfMonth(1);
        java.time.LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());

        long lateCountSoFarThisMonth = attendanceRepository
                .countByEmpIdAndStatusAndAttDateBetween(empId, "Late", monthStart, monthEnd);

        if (lateCountSoFarThisMonth >= 2) {
            return "Half Day";
        }
        return "Late";
    }

    // ---- Staff's own QR token (for display in their portal) ----

    public Long getEmpIdForUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        if (user.getEmployee() == null) {
            throw new RuntimeException("This account is not linked to an employee record.");
        }

        return user.getEmployee().getEmpId();
    }

    // ---- Automatic marking (called by scheduled job at 10 AM) ----
    // Skips: Sundays, and Dean/Administrator (they have no check-in method).
    // Anyone on approved leave today gets an "On Leave" record; everyone else gets "Absent".

    public void autoMarkAbsentees() {
        java.time.LocalDate today = java.time.LocalDate.now();

        if (today.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            return;
        }

        Set<Long> exemptEmpIds = getEmpIdsExemptFromAttendance();
        List<Employee> allEmployees = employeeRepository.findAll();

        for (Employee emp : allEmployees) {
            if (exemptEmpIds.contains(emp.getEmpId())) {
                continue;
            }

            boolean alreadyMarked = attendanceRepository
                    .findByEmpIdAndAttDate(emp.getEmpId(), today)
                    .isPresent();
            if (alreadyMarked) {
                continue;
            }

            boolean onApprovedLeave = leaveBalanceService
                    .getApprovedLeaveDays(emp.getEmpId(), today.getYear())
                    .contains(today);

            Attendance record = new Attendance();
            record.setEmpId(emp.getEmpId());
            record.setAttDate(today);
            record.setStatus(onApprovedLeave ? "On Leave" : "Absent");
            attendanceRepository.save(record);
        }
    }

    // Employees whose login role is Dean or Administrator.
    private Set<Long> getEmpIdsExemptFromAttendance() {
        Set<Long> exempt = new HashSet<>();
        for (User user : userRepository.findAll()) {
            if (user.getEmployee() == null || user.getRole() == null) {
                continue;
            }
            String roleName = user.getRole().name();
            if ("DEAN".equals(roleName) || "ADMINISTRATOR".equals(roleName)) {
                exempt.add(user.getEmployee().getEmpId());
            }
        }
        return exempt;
    }

    // ---- Missed checkout penalty (called by scheduled job at 6:01 PM) ----

    public void autoMarkMissedCheckouts() {
        java.time.LocalDate today = java.time.LocalDate.now();
        List<Attendance> todaysRecords = attendanceRepository.findAll().stream()
                .filter(a -> a.getAttDate().equals(today))
                .collect(Collectors.toList());

        for (Attendance att : todaysRecords) {
            boolean checkedInButNeverOut = att.getCheckIn() != null && att.getCheckOut() == null;
            boolean eligibleStatus = "Present".equals(att.getStatus()) || "Late".equals(att.getStatus());

            if (checkedInButNeverOut && eligibleStatus) {
                att.setStatus("Half Day");
                attendanceRepository.save(att);
            }
        }
    }

    // ---- Department scoping (for HOD portal) ----

    public List<Attendance> getAttendanceForMyDepartment(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        if (user.getEmployee() == null) {
            throw new RuntimeException("This account is not linked to an employee record.");
        }

        Department department = user.getEmployee().getDepartment();

        List<Long> deptEmpIds = employeeRepository.findByDepartment(department).stream()
                .map(Employee::getEmpId)
                .collect(Collectors.toList());

        return attendanceRepository.findByEmpIdIn(deptEmpIds);
    }
}