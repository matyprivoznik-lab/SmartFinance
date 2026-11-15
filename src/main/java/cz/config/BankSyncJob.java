package cz.config;

import cz.entity.BankConnection;
import cz.repository.BankConnectionRepository;
import cz.service.BankCheckService;
import cz.service.PlanQuotaService;
import cz.service.ReminderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Component
public class BankSyncJob {

    private final BankConnectionRepository bankConnectionRepository;
    private final BankCheckService bankCheckService;
    private final PlanQuotaService planQuotaService;

    public BankSyncJob(BankConnectionRepository bankConnectionRepository,
                       BankCheckService bankCheckService,
                       PlanQuotaService planQuotaService) {
        this.bankConnectionRepository = bankConnectionRepository;
        this.bankCheckService = bankCheckService;
        this.planQuotaService = planQuotaService;
    }

    @Scheduled(cron = "0 45 6 * * *", zone = "Europe/Prague")
    @Transactional
    public void readPayments() {
        LocalDate today = LocalDate.now(ReminderService.ZONE);
        Set<Long> seen = new HashSet<>();
        for (BankConnection connection : bankConnectionRepository.findByStatus(BankConnection.LINKED)) {
            if (connection.getCompany() == null || connection.getCompany().getOwner() == null
                    || !planQuotaService.isPro(connection.getCompany().getOwner())
                    || !seen.add(connection.getCompany().getId())) {
                continue;
            }
            try {
                bankCheckService.check(connection.getCompany().getOwner(), connection.getCompany(), today.minusDays(30), today);
            } catch (Exception ignored) {
                // Další firma se zkontroluje i když jedna banka neodpoví.
            }
        }
    }
}
