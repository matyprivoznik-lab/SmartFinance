package cz.service;

import cz.entity.Company;
import cz.entity.User;
import cz.repository.CompanyRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CompanyService {

    public static final String SESSION_KEY = "activeCompanyId";

    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @Transactional
    public List<Company> list(User user) {
        if (user == null) {
            return List.of();
        }
        ensure(user);
        return companyRepository.findByOwnerOrderByIdAsc(user);
    }

    @Transactional
    public Company current(User user, HttpServletRequest request) {
        if (user == null) {
            return null;
        }
        List<Company> companies = list(user);
        Long selected = selectedId(request);
        Company company = companies.stream()
                .filter(item -> item.getId().equals(selected))
                .findFirst()
                .orElseGet(() -> companies.stream()
                        .filter(item -> item.getIco() != null && item.getIco().equals(user.getIco()))
                        .findFirst()
                        .orElse(companies.get(0)));
        remember(request, company.getId());
        return company;
    }

    @Transactional
    public Company switchTo(User user, Long companyId, HttpServletRequest request) {
        Company company = companyRepository.findById(companyId)
                .filter(item -> item.getOwner() != null && item.getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Tuhle firmu u účtu nemáš."));
        remember(request, company.getId());
        applyToUser(company, user);
        return company;
    }

    @Transactional
    public Company add(User user, String name, String ico, String vatNumber, String address, HttpServletRequest request) {
        Company existing = ico == null ? null : companyRepository.findByOwnerAndIco(user, ico).orElse(null);
        if (existing != null) {
            return switchTo(user, existing.getId(), request);
        }
        Company company = new Company();
        company.setOwner(user);
        company.setName(name);
        company.setIco(ico);
        company.setVatNumber(vatNumber);
        company.setAddress(address);
        company.setVatPayer(vatNumber != null && !vatNumber.isBlank());
        company.setInvoiceNumberPattern(user.getInvoiceNumberPattern());
        company.setBankAccount(user.getBankAccount());
        company = companyRepository.save(company);
        remember(request, company.getId());
        applyToUser(company, user);
        return company;
    }

    @Transactional
    public void mirrorFromUser(User user, HttpServletRequest request) {
        Company company = current(user, request);
        if (company == null) {
            return;
        }
        company.setName(user.getName());
        company.setIco(user.getIco());
        company.setVatNumber(user.getVatNumber());
        company.setAddress(user.getAddress());
        company.setBankAccount(user.getBankAccount());
        company.setIban(user.getIban());
        company.setSwift(user.getSwift());
        company.setInvoiceNumberPattern(user.getInvoiceNumberPattern());
        company.setVatPayer(user.isVatPayer());
        company.setExpenseRate(user.getExpenseRate());
        company.setMainActivity(user.isMainActivity());
        companyRepository.save(company);
    }

    @Transactional
    public void updateFioToken(User user, HttpServletRequest request, String token) {
        Company company = current(user, request);
        company.setFioApiToken(token == null || token.isBlank() ? null : token.trim());
        companyRepository.save(company);
    }

    @Transactional
    public void updateReminders(User user, HttpServletRequest request, boolean enabled, Integer gentleDays, Integer formalDays) {
        Company company = current(user, request);
        company.setRemindersEnabled(enabled);
        if (gentleDays != null && gentleDays >= 0) {
            company.setGentleReminderDays(gentleDays);
        }
        if (formalDays != null && formalDays >= 0) {
            company.setFormalReminderDays(formalDays);
        }
        companyRepository.save(company);
    }

    @Transactional
    public Company ensure(User user) {
        List<Company> existing = companyRepository.findByOwnerOrderByIdAsc(user);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        Company company = new Company();
        company.setOwner(user);
        company.setName(user.getName() == null || user.getName().isBlank() ? user.getEmail() : user.getName());
        company.setIco(user.getIco());
        company.setVatNumber(user.getVatNumber());
        company.setAddress(user.getAddress());
        company.setBankAccount(user.getBankAccount());
        company.setIban(user.getIban());
        company.setSwift(user.getSwift());
        company.setInvoiceNumberPattern(user.getInvoiceNumberPattern());
        company.setVatPayer(user.isVatPayer());
        company.setExpenseRate(user.getExpenseRate());
        company.setMainActivity(user.isMainActivity());
        return companyRepository.save(company);
    }

    public void applyToUser(Company company, User user) {
        user.setName(company.getName());
        user.setIco(company.getIco());
        user.setVatNumber(company.getVatNumber());
        user.setAddress(company.getAddress());
        user.setBankAccount(company.getBankAccount());
        user.setIban(company.getIban());
        user.setSwift(company.getSwift());
        user.setInvoiceNumberPattern(company.getInvoiceNumberPattern());
        user.setVatPayer(company.isVatPayer());
        user.setExpenseRate(company.getExpenseRate());
        user.setMainActivity(company.isMainActivity());
    }

    private static Long selectedId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(SESSION_KEY);
        return value instanceof Long id ? id : null;
    }

    private static void remember(HttpServletRequest request, Long companyId) {
        if (request != null && companyId != null) {
            request.getSession(true).setAttribute(SESSION_KEY, companyId);
        }
    }
}
