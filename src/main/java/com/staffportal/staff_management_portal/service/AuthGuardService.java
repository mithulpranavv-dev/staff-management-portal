package com.staffportal.staff_management_portal.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Service
public class AuthGuardService {

    private static final long PRE_AUTH_TTL_MS = 5 * 60 * 1000L;
    private static final int MAX_FACE_ATTEMPTS = 5;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final long OTP_LOCK_MS = 15 * 60 * 1000L;

    private static class PreAuth {
        String username;
        long expiresAt;
        int failures;
    }

    private static class OtpState {
        int failures;
        long lockedUntil;
    }

    private final SecureRandom random = new SecureRandom();
    private final Map<String, PreAuth> preAuthTokens = new HashMap<>();
    private final Map<String, OtpState> otpStates = new HashMap<>();

    // ---- Pre-auth token (password step passed, face step pending) ----

    public synchronized String issuePreAuthToken(String username) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PreAuth>> it = preAuthTokens.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expiresAt < now) {
                it.remove();
            }
        }

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        PreAuth p = new PreAuth();
        p.username = username;
        p.expiresAt = now + PRE_AUTH_TTL_MS;
        preAuthTokens.put(token, p);
        return token;
    }

    // Returns the username the token belongs to, or null if missing, unknown or expired.
    public synchronized String resolvePreAuthToken(String token) {
        if (token == null) return null;
        PreAuth p = preAuthTokens.get(token);
        if (p == null) return null;
        if (p.expiresAt < System.currentTimeMillis()) {
            preAuthTokens.remove(token);
            return null;
        }
        return p.username;
    }

    // Returns how many face attempts are left. 0 means the token has been destroyed.
    public synchronized int recordFaceFailure(String token) {
        PreAuth p = preAuthTokens.get(token);
        if (p == null) return 0;
        p.failures++;
        int remaining = MAX_FACE_ATTEMPTS - p.failures;
        if (remaining <= 0) {
            preAuthTokens.remove(token);
            return 0;
        }
        return remaining;
    }

    public synchronized void consumePreAuthToken(String token) {
        if (token != null) {
            preAuthTokens.remove(token);
        }
    }

    // ---- OTP attempt limiting ----

    public synchronized boolean isOtpLocked(String username) {
        OtpState s = otpStates.get(username);
        if (s == null) return false;
        long now = System.currentTimeMillis();
        if (s.lockedUntil > now) return true;
        if (s.lockedUntil != 0) {
            otpStates.remove(username);
        }
        return false;
    }

    public synchronized void recordOtpFailure(String username) {
        OtpState s = otpStates.computeIfAbsent(username, k -> new OtpState());
        s.failures++;
        if (s.failures >= MAX_OTP_ATTEMPTS) {
            s.lockedUntil = System.currentTimeMillis() + OTP_LOCK_MS;
            s.failures = 0;
        }
    }

    public synchronized void clearOtpFailures(String username) {
        otpStates.remove(username);
    }
}