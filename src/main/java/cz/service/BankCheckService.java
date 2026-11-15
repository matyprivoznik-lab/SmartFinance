package cz.service;

import cz.entity.BankConnection;
import cz.entity.Company;
import cz.entity.User;
import cz.repository.BankConnectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class BankCheckService {

    private final BankConnectionRepository bankConnectionRepository;
    private final OpenBankingService openBankingService;
    private final PaymentMatchService paymentMatchService;

    public BankCheckService(BankConnectionRepository bankConnectionRepository,
                            OpenBankingService openBankingService,
                            PaymentMatchService paymentMatchService) {
        this.bankConnectionRepository = bankConnectionRepository;
        this.openBankingService = openBankingService;
        this.paymentMatchService = paymentMatchService;
    }

    @Transactional
    public PaymentMatchService.Result check(User user, Company company, LocalDate from, LocalDate to) {
        List<PaymentMatchService.BankRow> rows = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        int matched = 0;
        int skipped = 0;
        if (company != null && company.getFioApiToken() != null && !company.getFioApiToken().isBlank()) {
            try {
                PaymentMatchService.Result fio = paymentMatchService.importFio(user, company, from, to);
                matched += fio.matched();
                skipped += fio.skipped();
                notes.addAll(fio.notes());
            } catch (IllegalArgumentException ex) {
                notes.add(ex.getMessage());
            }
        }
        boolean linked = false;
        for (BankConnection connection : bankConnectionRepository.findByCompanyOrderByIdAsc(company)) {
            if (!connection.isLinked()) {
                continue;
            }
            linked = true;
            try {
                rows.addAll(openBankingService.transactions(connection, from, to));
            } catch (IllegalArgumentException ex) {
                notes.add(connection.getInstitutionName() + ": " + ex.getMessage());
            }
        }
        PaymentMatchService.Result rest = paymentMatchService.apply(user, company, rows);
        matched += rest.matched();
        skipped += rest.skipped();
        notes.addAll(rest.notes());
        if (matched == 0 && skipped == 0 && !linked && (company == null || company.getFioApiToken() == null || company.getFioApiToken().isBlank())) {
            throw new IllegalArgumentException(notes.isEmpty()
                    ? "Není propojená žádná banka. Propoj ji, ulož token Fio, nebo nahraj výpis."
                    : String.join(" ", notes));
        }
        return new PaymentMatchService.Result(matched, skipped, notes);
    }
}
