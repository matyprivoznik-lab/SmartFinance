package cz.config;

import cz.service.RecurringInvoiceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class RecurringInvoiceJob {

    private final RecurringInvoiceService recurringInvoiceService;

    public RecurringInvoiceJob(RecurringInvoiceService recurringInvoiceService) {
        this.recurringInvoiceService = recurringInvoiceService;
    }

    @Scheduled(cron = "0 15 6 * * *", zone = "Europe/Prague")
    public void issueDueInvoices() {
        recurringInvoiceService.issueDue(LocalDate.now(RecurringInvoiceService.ZONE));
    }
}
