package cz.controller;

import cz.entity.User;
import cz.repository.UserRepository;
import cz.service.AresService;
import cz.service.CompanyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CompanyController {

    private final UserRepository userRepository;
    private final CompanyService companyService;
    private final AresService aresService;

    public CompanyController(UserRepository userRepository, CompanyService companyService, AresService aresService) {
        this.userRepository = userRepository;
        this.companyService = companyService;
        this.aresService = aresService;
    }

    @PostMapping("/companies/switch")
    public String switchCompany(@RequestParam Long companyId,
                                @AuthenticationPrincipal UserDetails userDetails,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        User user = user(userDetails);
        if (user == null) {
            return "redirect:/login";
        }
        try {
            companyService.switchTo(user, companyId, request);
            userRepository.save(user);
            redirectAttributes.addFlashAttribute("successMessage", "Přepnuto na vybranou firmu. Doklady ostatních firem zůstávají stranou.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/companies")
    public String addCompany(@RequestParam String ico,
                             @AuthenticationPrincipal UserDetails userDetails,
                             HttpServletRequest request,
                             RedirectAttributes redirectAttributes) {
        User user = user(userDetails);
        if (user == null) {
            return "redirect:/login";
        }
        String normalized = AresService.normalizeIco(ico);
        AresService.AresResponse data = normalized == null ? null : aresService.getCompanyDetails(normalized);
        if (data == null || data.getName() == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Další firmu založíme jen z IČO, které je v ARES.");
            return "redirect:/settings";
        }
        companyService.add(user, data.getName(), data.getIco(), data.getVatNumber(), data.getAddress(), request);
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("successMessage", "Firma " + data.getName() + " je aktivní. Má vlastní doklady, náklady i číselnou řadu.");
        return "redirect:/settings";
    }

    private User user(UserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }
        return userRepository.findByEmail(userDetails.getUsername()).orElse(null);
    }
}
