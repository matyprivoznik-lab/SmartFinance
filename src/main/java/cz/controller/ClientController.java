package cz.controller;

import cz.entity.Client;
import cz.entity.Invoice;
import cz.entity.User;
import cz.repository.ClientRepository;
import cz.repository.InvoiceRepository;
import cz.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class ClientController {

    private final ClientRepository clientRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;

    public ClientController(ClientRepository clientRepository,
                            InvoiceRepository invoiceRepository,
                            UserRepository userRepository) {
        this.clientRepository = clientRepository;
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
    }

    // 1. Zobrazení seznamu všech klientů přihlášeného uživatele
    @GetMapping("/clients")
    public String showClients(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        String email = userDetails.getUsername();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        List<Client> clients = clientRepository.findByUser(currentUser);

        model.addAttribute("clients", clients);
        model.addAttribute("client", new Client());
        model.addAttribute("newClient", new Client()); // Řeší IllegalStateException v Thymeleafu pro newClient
        model.addAttribute("activePage", "clients");

        return "clients";
    }

    // 2. Uložení / vytvoření klienta
    @PostMapping("/clients/save")
    public String saveClient(@ModelAttribute Client client,
                             @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        String email = userDetails.getUsername();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        if (currentUser != null) {
            client.setUser(currentUser);
            clientRepository.save(client);
        }

        return "redirect:/clients";
    }

    // 3. Editace klienta (příprava formuláře)
    @GetMapping("/clients/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id,
                               @AuthenticationPrincipal UserDetails userDetails,
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

        List<Client> clients = clientRepository.findByUser(currentUser);

        model.addAttribute("clients", clients);
        model.addAttribute("client", client);
        model.addAttribute("newClient", client); // Pro jistotu, pokud formulář v editačním módu používá newClient
        model.addAttribute("activePage", "clients");

        return "clients";
    }

    // 4. Bezpečné smazání klienta (odpojení faktur bez jejich smazání)
    @PostMapping("/clients/delete/{id}")
    public String deleteClient(@PathVariable("id") Long id,
                               @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        String email = userDetails.getUsername();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        Client client = clientRepository.findById(id).orElse(null);

        if (client != null && currentUser != null && client.getUser() != null
                && client.getUser().getId().equals(currentUser.getId())) {

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