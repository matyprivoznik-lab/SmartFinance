package cz.service;

import cz.document.DocumentNumbers;
import cz.entity.User;
import cz.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SetupService {

    private final UserRepository userRepository;
    private final CompanyService companyService;
    private final AresService aresService;

    public SetupService(UserRepository userRepository, CompanyService companyService, AresService aresService) {
        this.userRepository = userRepository;
        this.companyService = companyService;
        this.aresService = aresService;
    }

    @Transactional
    public void finish(User user,
                       HttpServletRequest request,
                       String bankAccount,
                       String iban,
                       String swift,
                       String phone,
                       String note,
                       boolean mainActivity,
                       Double expenseRate,
                       boolean vatPayer,
                       String invoiceNumberPattern,
                       boolean remindersEnabled,
                       Integer gentleReminderDays,
                       Integer formalReminderDays,
                       String fioApiToken,
                       String extraIco) {
        if (bankAccount == null || bankAccount.isBlank()) {
            throw new IllegalArgumentException("Číslo účtu je potřeba, aby šla QR platba. Tvar je 123456789/0100.");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Telefon je potřeba, ať je na faktuře kontakt.");
        }
        String pattern = invoiceNumberPattern == null || invoiceNumberPattern.isBlank()
                ? DocumentNumbers.DEFAULT_PATTERN
                : invoiceNumberPattern.trim();
        if (!DocumentNumbers.isValid(pattern)) {
            throw new IllegalArgumentException("Formát čísla dokladu použij jako RRRR-CCC, FVRRRRCCCC nebo DRUHRRRRMM-CCC.");
        }

        user.setBankAccount(bankAccount.trim());
        user.setIban(blank(iban));
        user.setSwift(blank(swift));
        user.setPhone(blank(phone));
        user.setNote(blank(note));
        user.setMainActivity(mainActivity);
        if (expenseRate != null) {
            user.setExpenseRate(expenseRate);
        }
        user.setVatPayer(vatPayer);
        user.setInvoiceNumberPattern(pattern);
        userRepository.save(user);
        companyService.mirrorFromUser(user, request);
        companyService.updateReminders(user, request, remindersEnabled, gentleReminderDays, formalReminderDays);
        if (fioApiToken != null && !fioApiToken.isBlank()) {
            companyService.updateFioToken(user, request, fioApiToken);
        }
        if (extraIco != null && !extraIco.isBlank()) {
            String normalized = AresService.normalizeIco(extraIco);
            AresService.AresResponse data = normalized == null ? null : aresService.getCompanyDetails(normalized);
            if (data == null || data.getName() == null) {
                throw new IllegalArgumentException("Další firmu založíme jen z IČO, které je v ARES. Účet zatím zůstává v tomhle nastavení.");
            }
            companyService.add(user, data.getName(), data.getIco(), data.getVatNumber(), data.getAddress(), request);
            userRepository.save(user);
        }
        user.setSetupCompleted(true);
        userRepository.save(user);
    }

    @Transactional
    public void skip(User user) {
        user.setSetupCompleted(true);
        userRepository.save(user);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
