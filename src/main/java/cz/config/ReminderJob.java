package cz.config;

import cz.service.ReminderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class ReminderJob {

    private final ReminderService reminderService;

    public ReminderJob(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(cron = "0 30 7 * * *", zone = "Europe/Prague")
    public void sendReminders() {
        reminderService.sendDue(LocalDate.now(ReminderService.ZONE));
    }
}
