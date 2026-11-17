package com.staffportal.staff_management_portal.controller;

import com.staffportal.staff_management_portal.model.Attendance;
import com.staffportal.staff_management_portal.service.AttendanceService;
import com.staffportal.staff_management_portal.service.AttendanceAnomalyService;
import com.staffportal.staff_management_portal.service.QrTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private QrTokenService qrTokenService;

    @Autowired
    private AttendanceAnomalyService attendanceAnomalyService;

    @PostMapping
    public Attendance markAttendance(@RequestBody Attendance attendance) {
        return attendanceService.markAttendance(attendance);
    }

    @GetMapping
    public List<Attendance> getAllAttendance() {
        return attendanceService.getAllAttendance();
    }

    // Own attendance only: the person is taken from the login token
    @GetMapping("/me")
    public List<Attendance> getMyAttendance(Authentication authentication) {
        Long empId = attendanceService.getEmpIdForUsername(authentication.getName());
        return attendanceService.getAttendanceByEmployee(empId);
    }

    @GetMapping("/my-department")
    public List<Map<String, Object>> getMyDepartmentAttendance(Authentication authentication) {
        String username = authentication.getName();
        List<Attendance> records = attendanceService.getAttendanceForMyDepartment(username);

        // Strip check-in/check-out — HOD sees status only, per the spec
        return records.stream().map(att -> {
            Map<String, Object> map = new HashMap<>();
            map.put("attId", att.getAttId());
            map.put("empId", att.getEmpId());
            map.put("attDate", att.getAttDate());
            map.put("status", att.getStatus());
            return map;
        }).collect(Collectors.toList());
    }

    @GetMapping("/my-qr-token")
    public Map<String, Object> getMyQrToken(Authentication authentication) {
        Long empId = attendanceService.getEmpIdForUsername(authentication.getName());
        Map<String, Object> map = new HashMap<>();
        map.put("empId", empId);
        map.put("token", qrTokenService.getCurrentToken(empId));
        map.put("secondsRemaining", qrTokenService.getSecondsRemainingInWindow());
        return map;
    }

    @PostMapping("/scan")
    public ResponseEntity<?> scan(@RequestBody Map<String, Object> body) {
        try {
            Long empId = Long.valueOf(body.get("empId").toString());
            String token = (String) body.get("token");
            Attendance attendance = attendanceService.processScan(empId, token);
            return ResponseEntity.ok(attendance);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ---- Attendance Anomaly Detection ----

    @GetMapping("/anomalies/me")
    public List<Map<String, Object>> getMyAnomalies(Authentication authentication) {
        return attendanceAnomalyService.getAnomaliesForMe(authentication.getName());
    }

    @GetMapping("/anomalies/my-department")
    public List<Map<String, Object>> getMyDepartmentAnomalies(Authentication authentication) {
        return attendanceAnomalyService.getAnomaliesForMyDepartment(authentication.getName());
    }

    @GetMapping("/anomalies/all")
    public List<Map<String, Object>> getAllAnomalies() {
        return attendanceAnomalyService.getAnomaliesOrgWide();
    }

    @GetMapping("/employee/{empId}")
    public List<Attendance> getAttendanceByEmployee(@PathVariable Long empId) {
        return attendanceService.getAttendanceByEmployee(empId);
    }

    @PutMapping("/{attId}")
    public Attendance updateAttendance(@PathVariable Long attId, @RequestBody Attendance attendance) {
        return attendanceService.updateAttendance(attId, attendance);
    }

    @DeleteMapping("/{attId}")
    public String deleteAttendance(@PathVariable Long attId) {
        boolean deleted = attendanceService.deleteAttendance(attId);
        return deleted ? "Attendance record deleted successfully." : "Attendance record not found.";
    }

}