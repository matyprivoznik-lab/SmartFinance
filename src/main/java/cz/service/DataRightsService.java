package cz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cz.entity.Client;
import cz.entity.Invoice;
import cz.entity.InvoiceItem;
import cz.entity.User;
import cz.repository.ClientRepository;
import cz.repository.InvoiceRepository;
import cz.repository.ProductRepository;
import cz.repository.SubscriptionRepository;
import cz.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

@Service
public class DataRightsService {

    public static final String FORMAT = "minifaktura-invoices";
    public static final int VERSION = 1;
    public static final ZoneId ZONE = ZoneId.of("Europe/Prague");

    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PlanQuotaService planQuotaService;
    private final ObjectMapper objectMapper;

    public DataRightsService(InvoiceRepository invoiceRepository,
                             ClientRepository clientRepository,
                             ProductRepository productRepository,
                             SubscriptionRepository subscriptionRepository,
                             UserRepository userRepository,
                             PlanQuotaService planQuotaService,
                             ObjectMapper objectMapper) {
        this.invoiceRepository = invoiceRepository;
        this.clientRepository = clientRepository;
        this.productRepository = productRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.planQuotaService = planQuotaService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public byte[] exportInvoices(User user) {
        ObjectNode root = baseFile(user);
        root.set("invoices", invoiceArray(user));
        return write(root);
    }

    @Transactional(readOnly = true)
    public byte[] exportPersonalData(User user) {
        ObjectNode root = baseFile(user);
        root.set("account", accountNode(user));
        root.set("invoices", invoiceArray(user));
        root.put("note", "Export neobsahuje heslo ani tajemství dvoufázového ověření.");
        return write(root);
    }

    @Transactional
    public ImportResult importInvoices(User user, byte[] json) {
        if (json == null || json.length == 0) {
            throw new IllegalArgumentException("Soubor je prázdný.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Soubor není platný JSON export MiniFaktury.");
        }

        if (root.hasNonNull("format")) {
            String format = root.get("format").asText("");
            if (!FORMAT.equals(format)) {
                throw new IllegalArgumentException("Soubor není export faktur MiniFaktury.");
            }
        }

        JsonNode invoices = root.isArray() ? root : root.get("invoices");
        if (invoices == null || !invoices.isArray() || invoices.isEmpty()) {
            throw new IllegalArgumentException("Soubor neobsahuje žádné faktury.");
        }

        int imported = 0;
        int skippedDuplicate = 0;
        int skippedQuota = 0;
        int skippedInvalid = 0;

        for (JsonNode node : invoices) {
            String invoiceNumber = text(node, "invoiceNumber");
            if (invoiceNumber == null || invoiceNumber.isBlank()) {
                skippedInvalid++;
                continue;
            }
            invoiceNumber = invoiceNumber.trim();
            if (invoiceRepository.findByUserAndInvoiceNumber(user, invoiceNumber).isPresent()) {
                skippedDuplicate++;
                continue;
            }

            LocalDate issueDate = parseDate(node, "issueDate");
            if (issueDate == null) {
                issueDate = LocalDate.now(ZONE);
            }
            if (countsTowardQuota(issueDate) && !planQuotaService.canCreateInvoice(user)) {
                skippedQuota++;
                continue;
            }

            Invoice invoice = new Invoice();
            invoice.setUser(user);
            invoice.setInvoiceNumber(invoiceNumber);
            invoice.setVariableSymbol(text(node, "variableSymbol"));
            invoice.setConstantSymbol(text(node, "constantSymbol"));
            String paymentMethod = text(node, "paymentMethod");
            invoice.setPaymentMethod(paymentMethod == null || paymentMethod.isBlank() ? "Bankovní převod" : paymentMethod);
            invoice.setIssueDate(issueDate);
            invoice.setDueDate(parseDate(node, "dueDate"));
            LocalDate supplyDate = parseDate(node, "taxableSupplyDate");
            invoice.setTaxableSupplyDate(supplyDate != null ? supplyDate : issueDate);
            invoice.setStatus(parseStatus(text(node, "status")));
            invoice.setClient(resolveClient(user, node.get("client")));

            JsonNode items = node.get("items");
            if (items != null && items.isArray() && !items.isEmpty()) {
                double totalNet = 0.0;
                double gross = 0.0;
                for (JsonNode itemNode : items) {
                    InvoiceItem item = new InvoiceItem();
                    item.setDescription(text(itemNode, "description"));
                    double quantity = itemNode.path("quantity").asDouble(1.0);
                    double unitPriceGross = itemNode.path("unitPriceGross").asDouble(0.0);
                    double vatRate = itemNode.path("vatRate").asDouble(21.0);
                    double lineGross = roundMoney(quantity * unitPriceGross);
                    double net = vatRate <= 0.0 ? lineGross : roundMoney(lineGross / (1.0 + (vatRate / 100.0)));
                    item.setQuantity(quantity);
                    item.setUnitPriceGross(unitPriceGross);
                    item.setVatRate(vatRate);
                    item.setNetAmount(net);
                    item.setLineGrossTotal(lineGross);
                    invoice.addItem(item);
                    totalNet += net;
                    gross += lineGross;
                }
                invoice.setNetAmount(roundMoney(totalNet));
                invoice.setGrossTotal(roundMoney(gross));
                invoice.setVatAmount(roundMoney(gross - totalNet));
                invoice.setVatRate((int) Math.round(items.get(0).path("vatRate").asDouble(0.0)));
            } else {
                invoice.setNetAmount(node.path("netAmount").asDouble(0.0));
                invoice.setGrossTotal(node.path("grossTotal").asDouble(0.0));
                invoice.setVatAmount(node.path("vatAmount").asDouble(0.0));
                invoice.setVatRate(node.path("vatRate").asInt(0));
            }

            invoiceRepository.save(invoice);
            imported++;
        }

        return new ImportResult(imported, skippedDuplicate, skippedQuota, skippedInvalid);
    }

    @Transactional
    public void deleteAccount(User user) {
        User managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Účet už neexistuje."));

        List<Invoice> invoices = invoiceRepository.findByUser(managed);
        for (Invoice invoice : invoices) {
            if (invoice.getClient() != null) {
                invoice.getClient().getInvoices().remove(invoice);
                invoice.setClient(null);
            }
        }
        invoiceRepository.deleteAll(invoices);
        invoiceRepository.flush();
        clientRepository.deleteAll(clientRepository.findByUser(managed));
        productRepository.deleteAll(productRepository.findByUser(managed));
        subscriptionRepository.findByUser(managed).ifPresent(subscriptionRepository::delete);
        userRepository.delete(managed);
        userRepository.flush();
    }

    private ObjectNode baseFile(User user) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        root.put("exportedAt", LocalDateTime.now(ZONE).toString());
        root.put("accountEmail", user.getEmail());
        return root;
    }

    private ObjectNode accountNode(User user) {
        ObjectNode account = objectMapper.createObjectNode();
        account.put("email", user.getEmail());
        account.put("name", user.getName());
        account.put("ico", user.getIco());
        account.put("vatNumber", user.getVatNumber());
        account.put("address", user.getAddress());
        account.put("phone", user.getPhone());
        account.put("bankAccount", user.getBankAccount());
        account.put("iban", user.getIban());
        account.put("swift", user.getSwift());
        account.put("vatPayer", user.isVatPayer());
        account.put("expenseRate", user.getExpenseRate());
        account.put("mainActivity", user.isMainActivity());
        account.put("note", user.getNote());
        account.put("termsAccepted", user.isTermsAccepted());
        account.put("termsAcceptedAt", user.getTermsAcceptedAt() != null ? user.getTermsAcceptedAt().toString() : null);
        account.put("privacyAccepted", user.isPrivacyAccepted());
        account.put("privacyAcceptedAt", user.getPrivacyAcceptedAt() != null ? user.getPrivacyAcceptedAt().toString() : null);
        account.put("marketingConsent", user.isMarketingConsent());
        account.put("marketingConsentAt", user.getMarketingConsentAt() != null ? user.getMarketingConsentAt().toString() : null);
        return account;
    }

    private ArrayNode invoiceArray(User user) {
        ArrayNode invoices = objectMapper.createArrayNode();
        for (Invoice invoice : invoiceRepository.findByUser(user)) {
            ObjectNode node = invoices.addObject();
            node.put("invoiceNumber", invoice.getInvoiceNumber());
            node.put("variableSymbol", invoice.getVariableSymbol());
            node.put("constantSymbol", invoice.getConstantSymbol());
            node.put("paymentMethod", invoice.getPaymentMethod());
            node.put("issueDate", invoice.getIssueDate() != null ? invoice.getIssueDate().toString() : null);
            node.put("dueDate", invoice.getDueDate() != null ? invoice.getDueDate().toString() : null);
            node.put("taxableSupplyDate", invoice.getTaxableSupplyDate() != null ? invoice.getTaxableSupplyDate().toString() : null);
            node.put("grossTotal", invoice.getGrossTotal());
            node.put("netAmount", invoice.getNetAmount());
            node.put("vatAmount", invoice.getVatAmount());
            node.put("vatRate", invoice.getVatRate());
            node.put("status", invoice.getStatus() != null ? invoice.getStatus().name() : Invoice.InvoiceStatus.ISSUED.name());

            if (invoice.getClient() != null) {
                Client client = invoice.getClient();
                ObjectNode clientNode = node.putObject("client");
                clientNode.put("name", client.getName());
                clientNode.put("ico", client.getIco());
                clientNode.put("vatNumber", client.getVatNumber());
                clientNode.put("address", client.getAddress());
                clientNode.put("email", client.getEmail());
                clientNode.put("phone", client.getPhone());
            }

            ArrayNode items = node.putArray("items");
            if (invoice.getItems() != null) {
                for (InvoiceItem item : invoice.getItems()) {
                    ObjectNode itemNode = items.addObject();
                    itemNode.put("description", item.getDescription());
                    itemNode.put("quantity", item.getQuantity());
                    itemNode.put("unitPriceGross", item.getUnitPriceGross());
                    itemNode.put("vatRate", item.getVatRate());
                    itemNode.put("netAmount", item.getNetAmount());
                    itemNode.put("lineGrossTotal", item.getLineGrossTotal());
                }
            }
        }
        return invoices;
    }

    private Client resolveClient(User user, JsonNode clientNode) {
        if (clientNode == null || clientNode.isNull() || !clientNode.isObject()) {
            return null;
        }
        String name = text(clientNode, "name");
        String ico = text(clientNode, "ico");
        List<Client> existing = clientRepository.findByUser(user);
        if (ico != null && !ico.isBlank()) {
            for (Client client : existing) {
                if (ico.equals(client.getIco())) {
                    return client;
                }
            }
        }
        if (name != null && !name.isBlank()) {
            for (Client client : existing) {
                if (name.equalsIgnoreCase(client.getName())) {
                    return client;
                }
            }
        }
        if (name == null || name.isBlank()) {
            return null;
        }
        Client created = new Client();
        created.setUser(user);
        created.setName(name.trim());
        created.setIco(blankToNull(ico));
        created.setVatNumber(blankToNull(text(clientNode, "vatNumber")));
        created.setAddress(blankToNull(text(clientNode, "address")));
        created.setEmail(blankToNull(text(clientNode, "email")));
        created.setPhone(blankToNull(text(clientNode, "phone")));
        return clientRepository.save(created);
    }

    private byte[] write(ObjectNode root) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
        } catch (Exception ex) {
            throw new IllegalStateException("Export se nepodařilo sestavit.", ex);
        }
    }

    private static boolean countsTowardQuota(LocalDate issueDate) {
        YearMonth current = YearMonth.now(ZONE);
        return YearMonth.from(issueDate).equals(current);
    }

    private static Invoice.InvoiceStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return Invoice.InvoiceStatus.ISSUED;
        }
        try {
            return Invoice.InvoiceStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Invoice.InvoiceStatus.ISSUED;
        }
    }

    private static LocalDate parseDate(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            return null;
        }
        String iso = value.length() >= 10 ? value.substring(0, 10) : value;
        try {
            return LocalDate.parse(iso);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        String value = node.get(field).asText();
        return value == null ? null : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record ImportResult(int imported, int skippedDuplicate, int skippedQuota, int skippedInvalid) {
        public String message() {
            StringBuilder text = new StringBuilder();
            text.append("Import hotov: ").append(imported).append(imported == 1 ? " faktura uložena" : " faktur uloženo");
            if (skippedDuplicate > 0) {
                text.append(", ").append(skippedDuplicate).append(" přeskočeno (stejné číslo dokladu)");
            }
            if (skippedQuota > 0) {
                text.append(", ").append(skippedQuota).append(" se nevešlo do limitu Free plánu v tomto měsíci");
            }
            if (skippedInvalid > 0) {
                text.append(", ").append(skippedInvalid).append(" bez čísla dokladu");
            }
            text.append(".");
            return text.toString();
        }
    }
}
