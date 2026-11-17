package com.staffportal.staff_management_portal.controller;

import com.staffportal.staff_management_portal.service.PerformanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/performance")
public class PerformanceController {

    @Autowired
    private PerformanceService performanceService;

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    @GetMapping("/all")
    public ResponseEntity<?> all(Authentication auth) {
        if (!hasRole(auth, "DEAN")) {
            return ResponseEntity.status(403).body(Map.of("error", "Only the Dean can view this."));
        }
        return ResponseEntity.ok(performanceService.forAll());
    }

    @GetMapping("/my-department")
    public ResponseEntity<?> myDepartment(Authentication auth) {
        if (!hasRole(auth, "HEAD_OF_DEPARTMENT")) {
            return ResponseEntity.status(403).body(Map.of("error", "Only a Head of Department can view this."));
        }
        try {
            return ResponseEntity.ok(performanceService.forMyDepartment(auth.getName()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}