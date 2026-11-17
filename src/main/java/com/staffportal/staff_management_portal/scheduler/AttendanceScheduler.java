package com.staffportal.staff_management_portal.scheduler;

import com.staffportal.staff_management_portal.service.AttendanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class AttendanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(AttendanceScheduler.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    @Autowired
    private AttendanceService attendanceService;

    // Runs every day at 10:00:00 AM India time
    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Kolkata")
    public void markAbsentees() {
        log.info("Scheduled absentee sweep running");
        attendanceService.autoMarkAbsentees();
    }

    // Runs every day at 6:01:00 PM India time
    @Scheduled(cron = "0 1 18 * * *", zone = "Asia/Kolkata")
    public void markMissedCheckouts() {
        log.info("Scheduled missed-checkout sweep running");
        attendanceService.autoMarkMissedCheckouts();
    }

    // Catch-up: if the app was down at 10:00 or 18:01, run the missed job once on startup.
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        LocalDateTime now = LocalDateTime.now(INDIA);

        if (now.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return;
        }

        if (!now.toLocalTime().isBefore(LocalTime.of(10, 0))) {
            log.info("Startup catch-up: running absentee sweep");
            attendanceService.autoMarkAbsentees();
        }
        if (!now.toLocalTime().isBefore(LocalTime.of(18, 1))) {
            log.info("Startup catch-up: running missed-checkout sweep");
            attendanceService.autoMarkMissedCheckouts();
        }
    }
}