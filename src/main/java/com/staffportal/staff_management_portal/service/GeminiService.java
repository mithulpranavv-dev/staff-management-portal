package com.staffportal.staff_management_portal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent";

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] WAIT_BEFORE_RETRY_MS = {1500L, 3000L};

    private static final String BUSY_MESSAGE =
            "The AI service is busy right now. Please try again in a moment.";
    private static final String FAILED_MESSAGE =
            "Sorry, I couldn't process that right now.";

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public GeminiService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(25));
        this.restTemplate = new RestTemplate(factory);
    }

    @SuppressWarnings("unchecked")
    public String ask(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Map<String, Object> response = restTemplate.postForObject(ENDPOINT, request, Map.class);
                return extractText(response);

            } catch (HttpServerErrorException | ResourceAccessException e) {
                // 5xx (for example 503 "high demand") or timeout / network problem: worth retrying
                log.warn("Gemini call failed (attempt {}/{}): {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt == MAX_ATTEMPTS) {
                    return BUSY_MESSAGE;
                }
                sleepQuietly(WAIT_BEFORE_RETRY_MS[attempt - 1]);

            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    // Rate limited: retry like a busy server
                    log.warn("Gemini rate limit (attempt {}/{}): {}", attempt, MAX_ATTEMPTS, e.getMessage());
                    if (attempt == MAX_ATTEMPTS) {
                        return BUSY_MESSAGE;
                    }
                    sleepQuietly(WAIT_BEFORE_RETRY_MS[attempt - 1]);
                } else {
                    // Other 4xx (bad key, wrong model name, bad request): retrying will not help
                    log.error("Gemini rejected the request: status {} body {}",
                            e.getStatusCode().value(), e.getResponseBodyAsString());
                    return FAILED_MESSAGE;
                }

            } catch (Exception e) {
                log.error("Unexpected error calling Gemini", e);
                return FAILED_MESSAGE;
            }
        }
        return FAILED_MESSAGE;
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            return (String) parts.get(0).get("text");
        } catch (Exception e) {
            log.warn("Could not read Gemini response: {}", response);
            return FAILED_MESSAGE;
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}