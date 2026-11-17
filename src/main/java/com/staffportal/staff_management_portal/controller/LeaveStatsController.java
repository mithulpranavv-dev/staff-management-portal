package com.staffportal.staff_management_portal.controller;

import com.staffportal.staff_management_portal.service.LeaveStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/leave-stats")
public class LeaveStatsController {

    @Autowired
    private LeaveStatsService leaveStatsService;

    private boolean hasAnyRole(Authentication auth, String... roles) {
        for (String r : roles) {
            boolean match = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_" + r));
            if (match) return true;
        }
        return false;
    }

    private int resolveYear(Integer year) {
        return year != null ? year : LocalDate.now().getYear();
    }

    private ResponseEntity<?> forbidden(String message) {
        return ResponseEntity.status(403).body(Map.of("error", message));
    }

    // ---- Chart counts ----

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth, @RequestParam(required = false) Integer year) {
        try {
            return ResponseEntity.ok(leaveStatsService.forMe(auth.getName(), resolveYear(year)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/my-department")
    public ResponseEntity<?> myDepartment(Authentication auth, @RequestParam(required = false) Integer year) {
        if (!hasAnyRole(auth, "HEAD_OF_DEPARTMENT")) {
            return forbidden("Only a Head of Department can view this.");
        }
        try {
            return ResponseEntity.ok(leaveStatsService.forMyDepartment(auth.getName(), resolveYear(year)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> all(Authentication auth, @RequestParam(required = false) Integer year) {
        if (!hasAnyRole(auth, "DEAN", "ADMINISTRATOR")) {
            return forbidden("Only the Dean or Administrator can view this.");
        }
        return ResponseEntity.ok(leaveStatsService.forAll(resolveYear(year)));
    }

    // ---- Detailed records (click-to-expand) ----

    @GetMapping("/me/details")
    public ResponseEntity<?> meDetails(Authentication auth) {
        try {
            return ResponseEntity.ok(leaveStatsService.detailsForMe(auth.getName()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/my-department/details")
    public ResponseEntity<?> myDepartmentDetails(Authentication auth) {
        if (!hasAnyRole(auth, "HEAD_OF_DEPARTMENT")) {
            return forbidden("Only a Head of Department can view this.");
        }
        try {
            return ResponseEntity.ok(leaveStatsService.detailsForMyDepartment(auth.getName()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/all/details")
    public ResponseEntity<?> allDetails(Authentication auth) {
        if (!hasAnyRole(auth, "DEAN", "ADMINISTRATOR")) {
            return forbidden("Only the Dean or Administrator can view this.");
        }
        return ResponseEntity.ok(leaveStatsService.detailsForAll());
    }
}