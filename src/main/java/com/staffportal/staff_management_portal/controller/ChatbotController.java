package com.staffportal.staff_management_portal.controller;

import com.staffportal.staff_management_portal.service.ChatbotContextService;
import com.staffportal.staff_management_portal.service.GeminiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private static final int MAX_MESSAGE_LENGTH = 1000;

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private ChatbotContextService chatbotContextService;

    private static final String SYSTEM_PROMPT = """
            You are the AI assistant built into a college Staff Management Portal.
            The portal has these modules: Employees, Attendance, Leave Management,
            Payroll, and Tasks. Roles are: FACULTY_MEMBER (staff), HEAD_OF_DEPARTMENT (HOD),
            DEAN, and ADMINISTRATOR.

            How things work in this portal:
            - Staff apply for leave via the Leave page; Dean approves/rejects with a reason if rejected.
            - Staff view their own attendance, payroll (with deduction breakdowns), and assigned tasks.
            - HOD can view their own department's staff, attendance, and leave requests (view-only for leave),
              and can assign tasks to their department's staff.
            - Dean can view all employees, approve/reject leave, and assign tasks to anyone.
            - Administrator manages employee records (add/edit/delete) and payroll (generate payslips).

            You will be given a summary of the logged-in user's own relevant portal data below.
            Answer their questions using ONLY that data — do not make up numbers or records that
            aren't in the summary. If asked something you don't have data for, say so honestly and
            suggest which part of the portal they can check instead. Keep answers concise and friendly.
            """;

    @PostMapping("/ask")
    public ResponseEntity<Map<String, String>> ask(@RequestBody Map<String, String> body,
                                                   Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("reply", "Please log in again."));
        }

        String userMessage = body == null ? null : body.get("message");
        if (userMessage == null || userMessage.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("reply", "Please type a question."));
        }
        userMessage = userMessage.trim();
        if (userMessage.length() > MAX_MESSAGE_LENGTH) {
            return ResponseEntity.badRequest()
                    .body(Map.of("reply", "Your question is too long. Please keep it under "
                            + MAX_MESSAGE_LENGTH + " characters."));
        }

        // Identity always comes from the login token, never from the request body.
        String username = authentication.getName();
        String userContext = chatbotContextService.buildContext(username);

        String fullPrompt = SYSTEM_PROMPT
                + "\n\n--- Current user's portal data ---\n" + userContext
                + "\n\n--- User's question ---\n" + userMessage;

        String reply = geminiService.ask(fullPrompt);
        if (reply == null || reply.isBlank()) {
            reply = "Sorry, I could not get an answer right now. Please try again.";
        }
        return ResponseEntity.ok(Map.of("reply", reply));
    }
}