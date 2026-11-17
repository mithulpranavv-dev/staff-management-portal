package com.staffportal.staff_management_portal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    public void sendOtpEmail(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Staff Portal - Password Reset OTP");
        message.setText("Your OTP for password reset is: " + otp +
                "\n\nThis code is valid for 10 minutes. If you did not request this, please ignore this email.");

        try {
            mailSender.send(message);
        } catch (Exception e) {
            // The forgot-password response is intentionally generic, so don't surface mail errors to the caller
            log.error("Failed to send OTP email to {}", toEmail, e);
        }
    }
}