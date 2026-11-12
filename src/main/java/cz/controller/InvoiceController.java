package cz.controller;

import cz.entity.Client;
import cz.entity.Invoice;
import cz.entity.Product;
import cz.entity.User;
import cz.repository.ClientRepository;
import cz.repository.InvoiceRepository;
import cz.repository.ProductRepository;
import cz.repository.UserRepository;
import cz.service.PlanQuotaService;
import cz.service.QrPaymentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
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

    public InvoiceController(InvoiceRepository invoiceRepository,
                             ClientRepository clientRepository,
                             ProductRepository productRepository,
                             UserRepository userRepository,
                             PlanQuotaService planQuotaService,
                             QrPaymentService qrPaymentService) {
        this.invoiceRepository = invoiceRepository;
        this.clientRepository = clientRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.planQuotaService = planQuotaService;
        this.qrPaymentService = qrPaymentService;
    }

    @GetMapping("/invoices")
    public String showInvoices(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        User currentUser = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        List<Invoice> invoices = invoiceRepository.findByUser(currentUser);

        model.addAttribute("invoices", invoices);
        model.addAttribute("activePage", "invoices");
        return "invoices";
    }

    @GetMapping("/invoices/new")
    public String showCreateInvoiceForm(@AuthenticationPrincipal UserDetails userDetails,
                                        Model model,
                                        RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        User currentUser = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (currentUser == null) {
            return "redirect:/login";
        }

        if (!planQuotaService.canCreateInvoice(currentUser)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Free plán umožňuje max. " + PlanQuotaService.FREE_MONTHLY_INVOICE_LIMIT
                            + " faktur za měsíc. Upgraduj na Předplatné.");
            return "redirect:/billing";
        }

        List<Client> clients = clientRepository.findByUser(currentUser);
        List<Product> products = productRepository.findByUser(currentUser);

        LocalDate now = LocalDate.now();
        String yearMonth = String.format("%d%02d", now.getYear(), now.getMonthValue());

        List<Invoice> userInvoices = invoiceRepository.findByUser(currentUser);
        long countThisMonth = userInvoices.stream()
                .filter(i -> i.getInvoiceNumber() != null && i.getInvoiceNumber().startsWith(yearMonth))
                .count();

        String generatedNumber = String.format("%s-%03d", yearMonth, countThisMonth + 1);

        Invoice newInvoice = new Invoice();
        newInvoice.setInvoiceNumber(generatedNumber);
        newInvoice.setVariableSymbol(generatedNumber);
        newInvoice.setIssueDate(LocalDate.now());
        newInvoice.setTaxableSupplyDate(LocalDate.now());
        newInvoice.setDueDate(LocalDate.now().plusDays(14));

        model.addAttribute("invoice", newInvoice);
        model.addAttribute("clients", clients);
        model.addAttribute("products", products);
        model.addAttribute("activePage", "invoices");
        return "invoice-form";
    }

    @GetMapping("/invoices/{id}")
    public String showInvoiceDetail(@PathVariable("id") Long id,
                                    @AuthenticationPrincipal UserDetails userDetails,
                                    Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        User currentUser = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        Invoice invoice = invoiceRepository.findById(id).orElse(null);

        if (invoice == null || currentUser == null || invoice.getUser() == null
                || !invoice.getUser().getId().equals(currentUser.getId())) {
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
                              RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        User currentUser = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (currentUser == null) {
            return "redirect:/login";
        }

        boolean isNew = invoice.getId() == null;
        if (isNew && !planQuotaService.canCreateInvoice(currentUser)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Free plán umožňuje max. " + PlanQuotaService.FREE_MONTHLY_INVOICE_LIMIT
                            + " faktur za měsíc. Upgraduj na Předplatné.");
            return "redirect:/billing";
        }

        invoice.setUser(currentUser);

        if (clientId != null) {
            Client client = clientRepository.findById(clientId).orElse(null);
            if (client != null && client.getUser() != null
                    && client.getUser().getId().equals(currentUser.getId())) {
                invoice.setClient(client);
            } else {
                invoice.setClient(null);
            }
        } else {
            invoice.setClient(null);
        }

        if (invoice.getTaxableSupplyDate() == null) {
            invoice.setTaxableSupplyDate(invoice.getIssueDate() != null ? invoice.getIssueDate() : LocalDate.now());
        }

        double totalNetAmount = 0.0;
        double grossTotal = 0.0;

        if (invoice.getItems() != null) {
            for (var item : invoice.getItems()) {
                item.setInvoice(invoice);

                double quantity = item.getQuantity() != null ? item.getQuantity() : 1.0;
                double unitPriceGross = item.getUnitPriceGross() != null ? item.getUnitPriceGross() : 0.0;
                double vatRate = item.getVatRate() != null ? item.getVatRate() : 21.0;
                item.setVatRate(vatRate);

                double lineGrossTotal = roundMoney(quantity * unitPriceGross);
                double netAmount = vatRate <= 0.0
                        ? lineGrossTotal
                        : roundMoney(lineGrossTotal / (1.0 + (vatRate / 100.0)));

                item.setNetAmount(netAmount);
                item.setLineGrossTotal(lineGrossTotal);

                totalNetAmount += netAmount;
                grossTotal += lineGrossTotal;
            }
        }

        invoice.setNetAmount(roundMoney(totalNetAmount));
        invoice.setGrossTotal(roundMoney(grossTotal));
        invoice.setVatAmount(roundMoney(grossTotal - totalNetAmount));

        invoiceRepository.save(invoice);
        return "redirect:/invoices";
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

    private static double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
