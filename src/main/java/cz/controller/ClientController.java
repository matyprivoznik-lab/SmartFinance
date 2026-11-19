package cz.controller;

import cz.entity.Client;
import cz.entity.Company;
import cz.entity.Invoice;
import cz.entity.User;
import cz.repository.ClientRepository;
import cz.repository.InvoiceRepository;
import cz.repository.UserRepository;
import cz.service.CompanyService;
import cz.service.DirectorySearch;
import cz.service.RiskCheckService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class ClientController {

    private final ClientRepository clientRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final CompanyService companyService;
    private final RiskCheckService riskCheckService;

    public ClientController(ClientRepository clientRepository,
                            InvoiceRepository invoiceRepository,
                            UserRepository userRepository,
                            CompanyService companyService,
                            RiskCheckService riskCheckService) {
        this.clientRepository = clientRepository;
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
        this.companyService = companyService;
        this.riskCheckService = riskCheckService;
    }

    // 1. Zobrazení seznamu všech klientů přihlášeného uživatele
    @GetMapping("/clients")
    public String showClients(@AuthenticationPrincipal UserDetails userDetails,
                              HttpServletRequest request,
                              @RequestParam(value = "q", required = false) String query,
                              @RequestParam(value = "ico", required = false) String ico,
                              Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        String email = userDetails.getUsername();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        List<Client> clients = clientRepository.findByCompany(companyService.current(currentUser, request));
        addClientSearch(model, clients, query, ico);
        model.addAttribute("client", new Client());
        model.addAttribute("newClient", new Client()); // Řeší IllegalStateException v Thymeleafu pro newClient
        model.addAttribute("activePage", "clients");

        return "clients";
    }

    // 2. Uložení / vytvoření klienta
    @PostMapping("/clients/save")
    public String saveClient(@ModelAttribute Client client,
                             @AuthenticationPrincipal UserDetails userDetails,
                             HttpServletRequest request) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        String email = userDetails.getUsername();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        if (currentUser != null) {
            client.setUser(currentUser);
            if (client.getCompany() == null) {
                client.setCompany(companyService.current(currentUser, request));
            }
            clientRepository.save(client);
        }

        return "redirect:/clients";
    }

    // 3. Editace klienta (příprava formuláře)
    @GetMapping("/clients/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id,
                               @AuthenticationPrincipal UserDetails userDetails,
                               HttpServletRequest request,
                               Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        String email = userDetails.getUsername();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        Client client = clientRepository.findById(id).orElse(null);

        if (client == null || currentUser == null || client.getUser() == null
                || !client.getUser().getId().equals(currentUser.getId())) {
            return "redirect:/clients";
        }

        List<Client> clients = clientRepository.findByCompany(companyService.current(currentUser, request));

        addClientSearch(model, clients, null, null);
        model.addAttribute("client", client);
        model.addAttribute("newClient", client); // Pro jistotu, pokud formulář v editačním módu používá newClient
        model.addAttribute("activePage", "clients");

        return "clients";
    }

    private void addClientSearch(Model model, List<Client> clients, String query, String ico) {
        String q = DirectorySearch.clean(query);
        String selectedIco = DirectorySearch.clean(ico);
        Map<Long, String> clientSearch = new HashMap<>();
        Set<Long> shownIds = new HashSet<>();
        for (Client client : clients) {
            clientSearch.put(client.getId(), DirectorySearch.clientText(client));
            if (DirectorySearch.clientVisible(client, q, selectedIco)) {
                shownIds.add(client.getId());
            }
        }
        model.addAttribute("clients", clients);
        model.addAttribute("clientSearch", clientSearch);
        model.addAttribute("shownIds", shownIds);
        model.addAttribute("q", q);
        model.addAttribute("selectedIco", selectedIco);
        model.addAttribute("totalCount", clients.size());
        model.addAttribute("shownCount", shownIds.size());
    }

    @PostMapping("/clients/{id}/risk")
    public String checkRisk(@PathVariable("id") Long id,
                            @AuthenticationPrincipal UserDetails userDetails,
                            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        User currentUser = userDetails == null ? null : userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        Client client = clientRepository.findById(id).orElse(null);
        if (currentUser == null || client == null || client.getUser() == null
                || !client.getUser().getId().equals(currentUser.getId())) {
            return "redirect:/clients";
        }
        RiskCheckService.Risk risk = riskCheckService.check(client.getIco(), client.getVatNumber());
        client.setRiskInsolvency(risk.insolvency());
        client.setRiskInsolvencyDetail(risk.insolvencyDetail());
        client.setRiskUnreliableVat(risk.unreliableVat());
        client.setRiskCheckedAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Europe/Prague")));
        clientRepository.save(client);
        redirectAttributes.addFlashAttribute("successMessage", risk.insolvencyDetail());
        return "redirect:/clients";
    }

    // 4. Bezpečné smazání klienta (odpojení faktur bez jejich smazání)
    @PostMapping("/clients/delete/{id}")
    public String deleteClient(@PathVariable("id") Long id,
                               @AuthenticationPrincipal UserDetails userDetails,
                               HttpServletRequest request) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        String email = userDetails.getUsername();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        Client client = clientRepository.findById(id).orElse(null);

        Company active = currentUser == null ? null : companyService.current(currentUser, request);
        if (client != null && currentUser != null && client.getUser() != null
                && client.getUser().getId().equals(currentUser.getId())
                && (client.getCompany() == null || active == null || client.getCompany().getId().equals(active.getId()))) {

            // Vyhledat všechny faktury navázané na tohoto klienta
            List<Invoice> clientInvoices = invoiceRepository.findByClient(client);

            // Vynulovat odkaz na klienta u faktur (faktura zůstane uchována v DB)
            for (Invoice invoice : clientInvoices) {
                invoice.setClient(null);
                invoiceRepository.save(invoice);
            }

            // Bezpečně smazat klienta z adresáře
            clientRepository.delete(client);
        }

        return "redirect:/clients";
    }
}