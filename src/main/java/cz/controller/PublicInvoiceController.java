package cz.controller;

import cz.entity.Invoice;
import cz.repository.InvoiceRepository;
import cz.service.QrPaymentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PublicInvoiceController {

    private final InvoiceRepository invoiceRepository;
    private final QrPaymentService qrPaymentService;

    public PublicInvoiceController(InvoiceRepository invoiceRepository, QrPaymentService qrPaymentService) {
        this.invoiceRepository = invoiceRepository;
        this.qrPaymentService = qrPaymentService;
    }

    @GetMapping("/p/{token}")
    public String show(@PathVariable String token, Model model) {
        Invoice invoice = invoiceRepository.findByPublicToken(token).orElse(null);
        if (invoice == null) {
            return "public-missing";
        }
        model.addAttribute("invoice", invoice);
        model.addAttribute("qrCodeBase64", qrPaymentService.qrCodeDataUrl(invoice.getUser(), invoice));
        return "public-invoice";
    }
}
