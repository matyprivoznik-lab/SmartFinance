package cz.controller;

import cz.document.DocumentKind;
import cz.document.DocumentPricing;
import cz.document.PublicLinks;
import cz.document.VatRegime;
import cz.entity.Company;
import cz.entity.Client;
import cz.entity.Invoice;
import cz.entity.Product;
import cz.entity.RecurringTemplate;
import cz.entity.User;
import cz.repository.ClientRepository;
import cz.repository.InvoiceRepository;
import cz.repository.ProductRepository;
import cz.repository.RecurringTemplateRepository;
import cz.repository.UserRepository;
import cz.service.CnbExchangeRateService;
import cz.service.CompanyService;
import cz.service.DataRightsService;
import cz.service.DocumentNumberService;
import cz.service.DocumentWorkflowService;
import cz.service.PlanQuotaService;
import cz.service.QrPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
public class InvoiceController {

    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PlanQuotaService planQuotaService;
    private final QrPaymentService qrPaymentService;
    private final DataRightsService dataRightsService;
    private final DocumentNumberService documentNumberService;
    private final DocumentWorkflowService documentWorkflowService;
    private final CnbExchangeRateService cnbExchangeRateService;
    private final RecurringTemplateRepository recurringTemplateRepository;
    private final CompanyService companyService;

    @PersistenceContext
    private EntityManager entityManager;

    public InvoiceController(InvoiceRepository invoiceRepository,
                             ClientRepository clientRepository,
                             ProductRepository productRepository,
                             UserRepository userRepository,
                             PlanQuotaService planQuotaService,
                             QrPaymentService qrPaymentService,
                             DataRightsService dataRightsService,
                             DocumentNumberService documentNumberService,
                             DocumentWorkflowService documentWorkflowService,
                             CnbExchangeRateService cnbExchangeRateService,
                             RecurringTemplateRepository recurringTemplateRepository,
                             CompanyService companyService) {
        this.invoiceRepository = invoiceRepository;
        this.clientRepository = clientRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.planQuotaService = planQuotaService;
        this.qrPaymentService = qrPaymentService;
        this.dataRightsService = dataRightsService;
        this.documentNumberService = documentNumberService;
        this.documentWorkflowService = documentWorkflowService;
        this.cnbExchangeRateService = cnbExchangeRateService;
        this.recurringTemplateRepository = recurringTemplateRepository;
        this.companyService = companyService;
    }

    @GetMapping("/invoices")
    public String showInvoices(@AuthenticationPrincipal UserDetails userDetails, HttpServletRequest request, Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        User currentUser = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        Company company = companyService.current(currentUser, request);
        List<Invoice> invoices = invoiceRepository.findByCompany(company);

        model.addAttribute("invoices", invoices);
        model.addAttribute("activePage", "invoices");
        return "invoices";
    }

    @GetMapping("/invoices/new")
    public String showCreateInvoiceForm(@RequestParam(value = "kind", required = false) DocumentKind kind,
                                        @AuthenticationPrincipal UserDetails userDetails,
                                        HttpServletRequest request,
                                        Model model,
                                        RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        User currentUser = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (currentUser == null) {
            return "redirect:/login";
        }

        DocumentKind documentKind = kind == null ? DocumentKind.INVOICE : kind;
        if (documentKind != DocumentKind.INVOICE && !planQuotaService.isPro(currentUser)) {
            return premium(redirectAttributes);
        }
        if (documentKind.countsTowardQuota() && !planQuotaService.canCreateInvoice(currentUser)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Freemium umožňuje max. " + PlanQuotaService.FREE_MONTHLY_INVOICE_LIMIT
                            + " faktur za měsíc. Premium má neomezený počet.");
            return "redirect:/billing";
        }

        Company company = companyService.current(currentUser, request);
        List<Client> clients = clientRepository.findByCompany(company);
        List<Product> products = productRepository.findByCompany(company);

        LocalDate now = LocalDate.now();
        String generatedNumber = documentNumberService.next(currentUser, company, documentKind, now);

        Invoice newInvoice = new Invoice();
        newInvoice.setDocumentKind(documentKind);
        newInvoice.setVatRegime(currentUser.isVatPayer() ? VatRegime.PAYER : VatRegime.NON_PAYER);
        newInvoice.setCurrency("CZK");
        newInvoice.setInvoiceNumber(generatedNumber);
        newInvoice.setVariableSymbol(generatedNumber == null ? null : generatedNumber.replaceAll("\\D", ""));
        newInvoice.setIssueDate(LocalDate.now());
        newInvoice.setTaxableSupplyDate(LocalDate.now());
        newInvoice.setDueDate(LocalDate.now().plusDays(14));

        model.addAttribute("invoice", newInvoice);
        model.addAttribute("clients", clients);
        model.addAttribute("products", products);
        model.addAttribute("documentKinds", planQuotaService.isPro(currentUser)
                ? DocumentKind.values()
                : new DocumentKind[]{DocumentKind.INVOICE});
        model.addAttribute("currencies", planQuotaService.isPro(currentUser)
                ? List.of("CZK", "EUR", "USD", "GBP")
                : List.of("CZK"));
        model.addAttribute("vatRegimes", VatRegime.values());
        model.addAttribute("activePage", "invoices");
        return "invoice-form";
    }

    @GetMapping("/invoices/{id}")
    public String showInvoiceDetail(@PathVariable("id") Long id,
                                    @AuthenticationPrincipal UserDetails userDetails,
                                    HttpServletRequest request,
                                    Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        User currentUser = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        Invoice invoice = invoiceRepository.findById(id).orElse(null);

        Company company = companyService.current(currentUser, request);
        if (invoice == null || currentUser == null || invoice.getUser() == null
                || !invoice.getUser().getId().equals(currentUser.getId())
                || (invoice.getCompany() != null && company != null && !invoice.getCompany().getId().equals(company.getId()))) {
            return "redirect:/invoices";
        }

        model.addAttribute("invoice", invoice);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("paymentIban", qrPaymentService.paymentIban(invoice.getUser()));
        model.addAttribute("qrCodeBase64", qrPaymentService.qrCodeDataUrl(invoice.getUser(), invoice));
        model.addAttribute("activePage", "invoices");
        return "invoice-detail";
    }

    @PostMapping("/invoices/save")
    public String saveInvoice(@ModelAttribute Invoice invoice,
                              @RequestParam(value = "clientId", required = false) Long clientId,
                              @AuthenticationPrincipal UserDetails userDetails,
                              HttpServletRequest request,
                              RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        User currentUser = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (currentUser == null) {
            return "redirect:/login";
        }

        boolean isNew = invoice.getId() == null;
        if (invoice.getDocumentKind() == null) {
            invoice.setDocumentKind(DocumentKind.INVOICE);
        }
        if (invoice.getVatRegime() == null) {
            invoice.setVatRegime(currentUser.isVatPayer() ? VatRegime.PAYER : VatRegime.NON_PAYER);
        }
        if (invoice.getCurrency() == null || invoice.getCurrency().isBlank()) {
            invoice.setCurrency("CZK");
        } else {
            invoice.setCurrency(invoice.getCurrency().trim().toUpperCase());
        }
        if (invoice.getDocumentKind() != DocumentKind.INVOICE && !planQuotaService.isPro(currentUser)) {
            return premium(redirectAttributes);
        }
        if (!"CZK".equalsIgnoreCase(invoice.getCurrency()) && !planQuotaService.isPro(currentUser)) {
            return premium(redirectAttributes);
        }
        if (isNew && invoice.getDocumentKind().countsTowardQuota() && !planQuotaService.canCreateInvoice(currentUser)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Freemium umožňuje max. " + PlanQuotaService.FREE_MONTHLY_INVOICE_LIMIT
                            + " faktur za měsíc. Premium má neomezený počet.");
            return "redirect:/billing";
        }

        invoice.setUser(currentUser);
        Company company = companyService.current(currentUser, request);
        Client postedClient = invoice.getClient();
        if (isNew) {
            invoice.setCompany(company);
            PublicLinks.ensure(invoice);
            invoice.captureSupplier(currentUser);
            invoice.setClient(resolveClient(currentUser, company, clientId, postedClient));
            invoice.captureCustomer(invoice.getClient());
            if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()) {
                invoice.setInvoiceNumber(documentNumberService.next(currentUser, company, invoice.getDocumentKind(), invoice.getIssueDate()));
            }
        } else {
            Invoice stored = invoiceRepository.findById(invoice.getId()).orElse(null);
            if (stored == null || stored.getUser() == null
                    || !stored.getUser().getId().equals(currentUser.getId())) {
                return "redirect:/invoices";
            }
            invoice.copySupplierFrom(stored);
            invoice.copyCustomerFrom(stored);
            invoice.setCompany(stored.getCompany());
            invoice.setPublicToken(stored.getPublicToken());
            invoice.setPaidAt(stored.getPaidAt());
            invoice.setGentleReminderSentAt(stored.getGentleReminderSentAt());
            invoice.setFormalReminderSentAt(stored.getFormalReminderSentAt());
            invoice.setDocumentKind(stored.getDocumentKind());
            invoice.setSourceDocument(stored.getSourceDocument());
            Client keptClient = stored.getClient();
            entityManager.detach(stored);
            invoice.setClient(clientId == null ? keptClient : resolveClient(currentUser, company, clientId, null));
        }

        if (invoice.getTaxableSupplyDate() == null) {
            invoice.setTaxableSupplyDate(invoice.getIssueDate() != null ? invoice.getIssueDate() : LocalDate.now());
        }

        DocumentPricing.apply(invoice);
        if (!"CZK".equalsIgnoreCase(invoice.getCurrency())) {
            var rate = cnbExchangeRateService.rate(invoice.getCurrency(), invoice.getTaxableSupplyDate());
            if (rate.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Kurz ČNB pro " + invoice.getCurrency() + " se nepodařilo načíst. Doklad nebyl uložen.");
                return "redirect:/invoices/new?kind=" + invoice.getDocumentKind().name();
            }
            DocumentPricing.applyRate(invoice, rate.get().czkPerUnit(), rate.get().date());
        }

        invoiceRepository.save(invoice);
        return "redirect:/invoices/" + invoice.getId();
    }

    @PostMapping("/invoices/{id}/follow-up")
    public String followUp(@PathVariable("id") Long id,
                           @AuthenticationPrincipal UserDetails userDetails,
                           RedirectAttributes redirectAttributes) {
        User currentUser = currentUser(userDetails);
        if (currentUser == null) {
            return "redirect:/login";
        }
        if (!planQuotaService.isPro(currentUser)) {
            return premium(redirectAttributes);
        }
        Invoice source = invoiceRepository.findById(id).orElse(null);
        try {
            Invoice created = documentWorkflowService.followUp(currentUser, source);
            redirectAttributes.addFlashAttribute("successMessage", created.getDocumentKind().getLabel() + " " + created.getInvoiceNumber() + " je vystavená. Starší doklad zůstal beze změny.");
            return "redirect:/invoices/" + created.getId();
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/invoices/" + id;
        }
    }

    @GetMapping("/invoices/recurring")
    public String recurring(@AuthenticationPrincipal UserDetails userDetails, HttpServletRequest request, Model model) {
        User currentUser = currentUser(userDetails);
        if (currentUser == null) {
            return "redirect:/login";
        }
        Company company = companyService.current(currentUser, request);
        model.addAttribute("templates", recurringTemplateRepository.findByCompanyOrderByNextIssueDateAsc(company));
        model.addAttribute("clients", clientRepository.findByCompany(company));
        model.addAttribute("vatRegimes", VatRegime.values());
        model.addAttribute("activePage", "invoices");
        return "recurring";
    }

    @PostMapping("/invoices/recurring")
    public String saveRecurring(@RequestParam(required = false) Long clientId,
                                @RequestParam String title,
                                @RequestParam String description,
                                @RequestParam(defaultValue = "1") Double quantity,
                                @RequestParam Double unitPriceGross,
                                @RequestParam(defaultValue = "21") Double vatRate,
                                @RequestParam(defaultValue = "PAYER") VatRegime vatRegime,
                                @RequestParam(defaultValue = "MONTHLY") RecurringTemplate.Interval interval,
                                @RequestParam LocalDate nextIssueDate,
                                @AuthenticationPrincipal UserDetails userDetails,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        User currentUser = currentUser(userDetails);
        if (currentUser == null) {
            return "redirect:/login";
        }
        if (title == null || title.isBlank() || description == null || description.isBlank() || unitPriceGross == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Šablona potřebuje název, popis a cenu.");
            return "redirect:/invoices/recurring";
        }
        Company company = companyService.current(currentUser, request);
        RecurringTemplate template = new RecurringTemplate();
        template.setUser(currentUser);
        template.setCompany(company);
        template.setClient(resolveClient(currentUser, company, clientId, null));
        template.setTitle(title.trim());
        template.setDescription(description.trim());
        template.setQuantity(quantity == null ? 1.0 : quantity);
        template.setUnitPriceGross(unitPriceGross);
        template.setVatRate(vatRate);
        template.setVatRegime(vatRegime);
        template.setInterval(interval);
        template.setNextIssueDate(nextIssueDate == null ? LocalDate.now() : nextIssueDate);
        template.setActive(true);
        recurringTemplateRepository.save(template);
        redirectAttributes.addFlashAttribute("successMessage", "Šablona je uložená. Faktura vznikne v den " + template.getNextIssueDate() + ".");
        return "redirect:/invoices/recurring";
    }

    @PostMapping("/invoices/delete/{id}")
    public String deleteInvoice(@PathVariable("id") Long id,
                                @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        User currentUser = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        Invoice invoice = invoiceRepository.findById(id).orElse(null);

        if (invoice != null && currentUser != null && invoice.getUser() != null
                && invoice.getUser().getId().equals(currentUser.getId())) {
            invoiceRepository.delete(invoice);
        }

        return "redirect:/invoices";
    }

    @GetMapping("/invoices/export")
    public ResponseEntity<byte[]> exportInvoices(@AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = currentUser(userDetails);
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        byte[] body = dataRightsService.exportInvoices(currentUser);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mujdokladomat-faktury.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @PostMapping("/invoices/import")
    public String importInvoices(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "next", defaultValue = "invoices") String next,
                                 @AuthenticationPrincipal UserDetails userDetails,
                                 RedirectAttributes redirectAttributes) {
        String target = "settings".equals(next) ? "redirect:/settings" : "redirect:/invoices";
        User currentUser = currentUser(userDetails);
        if (currentUser == null) {
            return "redirect:/login";
        }
        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vyberte JSON soubor s fakturami.");
            return target;
        }
        try {
            DataRightsService.ImportResult result = dataRightsService.importInvoices(currentUser, file.getBytes());
            redirectAttributes.addFlashAttribute("successMessage", result.message());
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Import se nepodařil. Zkontrolujte, že jde o export z MiniFaktury.");
        }
        return target;
    }

    private static String premium(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", PlanQuotaService.PREMIUM_LOCKED);
        return "redirect:/billing";
    }

    private User currentUser(UserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }
        return userRepository.findByEmail(userDetails.getUsername()).orElse(null);
    }

    private Client resolveClient(User user, Company company, Long clientId, Client posted) {
        Client client = null;
        if (clientId != null) {
            client = clientRepository.findById(clientId)
                    .filter(found -> found.getUser() != null && found.getUser().getId().equals(user.getId()))
                    .filter(found -> found.getCompany() == null || company == null || found.getCompany().getId().equals(company.getId()))
                    .orElse(null);
        }
        if (client == null && posted != null && posted.getName() != null && !posted.getName().isBlank()) {
            client = new Client();
            client.setUser(user);
            client.setCompany(company);
        }
        if (client == null || posted == null) {
            return client;
        }
        if (posted.getName() != null && !posted.getName().isBlank()) {
            client.setName(posted.getName().trim());
        }
        client.setIco(blank(posted.getIco()));
        client.setVatNumber(blank(posted.getVatNumber()));
        client.setAddress(blank(posted.getAddress()));
        client.setEmail(blank(posted.getEmail()));
        client.setPhone(blank(posted.getPhone()));
        return clientRepository.save(client);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
