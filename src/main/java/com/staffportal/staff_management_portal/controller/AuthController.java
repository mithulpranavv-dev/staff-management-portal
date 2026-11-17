package com.staffportal.staff_management_portal.controller;

import com.staffportal.staff_management_portal.config.JwtUtil;
import com.staffportal.staff_management_portal.model.User;
import com.staffportal.staff_management_portal.repository.UserRepository;
import com.staffportal.staff_management_portal.service.AuthGuardService;
import com.staffportal.staff_management_portal.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AuthGuardService authGuard;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(), loginRequest.getPassword())
            );
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body("Invalid username or password");
        }

        User user = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // If this account has a face enrolled, the password alone is not enough.
        // Hand back a short-lived pre-auth token that the face step must present.
        if (user.getFaceDescriptor() != null && !user.getFaceDescriptor().isBlank()) {
            Map<String, Object> response = new HashMap<>();
            response.put("requiresFaceVerification", true);
            response.put("username", user.getUsername());
            response.put("preAuthToken", authGuard.issuePreAuthToken(user.getUsername()));
            return ResponseEntity.ok(response);
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("username", user.getUsername());
        response.put("role", user.getRole().name());
        response.put("mustChangePassword", user.isMustChangePassword());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-face")
    public ResponseEntity<?> verifyFace(@RequestBody VerifyFaceRequest request) {
        String preAuthToken = request.getPreAuthToken();

        // The username comes from the token, never from the request body.
        String username = authGuard.resolvePreAuthToken(preAuthToken);
        if (username == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Your login session has expired. Please log in again.",
                    "expired", true));
        }

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            authGuard.consumePreAuthToken(preAuthToken);
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Your login session has expired. Please log in again.",
                    "expired", true));
        }

        if (user.getFaceDescriptor() == null || user.getFaceDescriptor().isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "No face enrolled for this account."));
        }

        if (request.getDescriptor() == null || request.getDescriptor().isEmpty()) {
            return ResponseEntity.status(400).body(Map.of("error", "No face data received. Please try again."));
        }

        double[] storedDescriptor = parseDescriptor(user.getFaceDescriptor());
        double[] scannedDescriptor = request.getDescriptor().stream().mapToDouble(Double::doubleValue).toArray();

        if (storedDescriptor.length != scannedDescriptor.length) {
            return ResponseEntity.status(400).body(Map.of("error", "Face data mismatch. Please contact Admin."));
        }

        double distance = euclideanDistance(storedDescriptor, scannedDescriptor);
        double MATCH_THRESHOLD = 0.45; // tightened from the 0.6 default after real-world testing showed false matches

        if (distance > MATCH_THRESHOLD) {
            int remaining = authGuard.recordFaceFailure(preAuthToken);
            if (remaining <= 0) {
                return ResponseEntity.status(401).body(Map.of(
                        "error", "Too many failed attempts. Please log in again.",
                        "matched", false,
                        "expired", true));
            }
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Face does not match. " + remaining + " attempt(s) left.",
                    "matched", false,
                    "attemptsLeft", remaining));
        }

        // Success: the pre-auth token is single use.
        authGuard.consumePreAuthToken(preAuthToken);

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());

        Map<String, Object> response = new HashMap<>();
        response.put("matched", true);
        response.put("token", token);
        response.put("username", user.getUsername());
        response.put("role", user.getRole().name());
        response.put("mustChangePassword", user.isMustChangePassword());

        return ResponseEntity.ok(response);
    }

    private double[] parseDescriptor(String csv) {
        String[] parts = csv.split(",");
        double[] result = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Double.parseDouble(parts[i].trim());
        }
        return result;
    }

    private double euclideanDistance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.pow(a[i] - b[i], 2);
        }
        return Math.sqrt(sum);
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request,
                                            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body("Please log in first");
        }
        String username = authentication.getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            return ResponseEntity.status(401).body("Old password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        return ResponseEntity.ok("Password changed successfully");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElse(null);

        // Always return the same generic message, whether the user exists or not —
        // this prevents attackers from using this endpoint to guess valid usernames.
        String genericResponse = "If this account exists, an OTP has been sent to the registered email.";

        if (user == null || user.getEmployee() == null || user.getEmployee().getEmail() == null) {
            return ResponseEntity.ok(genericResponse);
        }

        String otp = generateOtp();
        user.setOtp(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        emailService.sendOtpEmail(user.getEmployee().getEmail(), otp);

        return ResponseEntity.ok(genericResponse);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody VerifyOtpRequest request) {
        ResponseEntity<?> problem = checkOtp(request.getUsername(), request.getOtp());
        if (problem != null) {
            return problem;
        }
        return ResponseEntity.ok("OTP verified successfully");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        ResponseEntity<?> problem = checkOtp(request.getUsername(), request.getOtp());
        if (problem != null) {
            return problem;
        }

        User user = userRepository.findByUsername(request.getUsername()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(400).body("Invalid OTP");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        user.setOtp(null);
        user.setOtpExpiry(null);
        userRepository.save(user);
        authGuard.clearOtpFailures(user.getUsername());

        return ResponseEntity.ok("Password reset successfully");
    }

    // Shared OTP check. Returns null when the OTP is valid, otherwise the error response.
    // Unknown usernames get the same answer as a wrong OTP, so usernames cannot be probed.
    private ResponseEntity<?> checkOtp(String username, String submittedOtp) {
        if (username == null || submittedOtp == null) {
            return ResponseEntity.status(400).body("Invalid OTP");
        }

        if (authGuard.isOtpLocked(username)) {
            return ResponseEntity.status(429).body("Too many wrong attempts. Please try again in 15 minutes.");
        }

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return ResponseEntity.status(400).body("Invalid OTP");
        }

        if (user.getOtp() == null || user.getOtpExpiry() == null) {
            return ResponseEntity.status(400).body("No OTP requested for this account");
        }

        if (LocalDateTime.now().isAfter(user.getOtpExpiry())) {
            return ResponseEntity.status(400).body("OTP has expired. Please request a new one.");
        }

        boolean matches = MessageDigest.isEqual(
                user.getOtp().getBytes(StandardCharsets.UTF_8),
                submittedOtp.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            authGuard.recordOtpFailure(username);
            return ResponseEntity.status(400).body("Invalid OTP");
        }

        return null;
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        int otp = 100000 + random.nextInt(900000); // always 6 digits
        return String.valueOf(otp);
    }

    // ---- Request DTOs ----

    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;

        public String getOldPassword() { return oldPassword; }
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    public static class ForgotPasswordRequest {
        private String username;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
    }

    public static class VerifyOtpRequest {
        private String username;
        private String otp;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getOtp() { return otp; }
        public void setOtp(String otp) { this.otp = otp; }
    }

    public static class ResetPasswordRequest {
        private String username;
        private String otp;
        private String newPassword;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getOtp() { return otp; }
        public void setOtp(String otp) { this.otp = otp; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    public static class VerifyFaceRequest {
        private String username; // no longer used: the username now comes from the pre-auth token
        private String preAuthToken;
        private java.util.List<Double> descriptor;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPreAuthToken() { return preAuthToken; }
        public void setPreAuthToken(String preAuthToken) { this.preAuthToken = preAuthToken; }
        public java.util.List<Double> getDescriptor() { return descriptor; }
        public void setDescriptor(java.util.List<Double> descriptor) { this.descriptor = descriptor; }
    }
}