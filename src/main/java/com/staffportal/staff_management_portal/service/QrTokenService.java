package com.staffportal.staff_management_portal.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class QrTokenService {

    @Value("${qr.secret}")
    private String secret;

    private static final long WINDOW_MILLIS = 30_000;

    public String getCurrentToken(Long empId) {
        return generateToken(empId, currentWindow());
    }

    public long getSecondsRemainingInWindow() {
        long msIntoWindow = System.currentTimeMillis() % WINDOW_MILLIS;
        return (WINDOW_MILLIS - msIntoWindow) / 1000;
    }

    public boolean isValid(Long empId, String token) {
        long window = currentWindow();
        // Accept current window and the previous one, to tolerate scan/network delay
        return token.equals(generateToken(empId, window)) || token.equals(generateToken(empId, window - 1));
    }

    private long currentWindow() {
        return System.currentTimeMillis() / WINDOW_MILLIS;
    }

    private String generateToken(Long empId, long window) {
        try {
            String raw = secret + ":" + empId + ":" + window;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}