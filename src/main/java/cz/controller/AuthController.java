package cz.controller;

import cz.entity.User;
import cz.repository.UserRepository;
import cz.service.AresService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AresService aresService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("step", 1); // Krok 1: Zmíněné zadání IČO
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register/verify-ico")
    public String verifyIco(@RequestParam("ico") String ico, Model model) {
        if (ico == null || ico.trim().isEmpty()) {
            model.addAttribute("error", "Zadejte platné IČO.");
            model.addAttribute("step", 1);
            model.addAttribute("user", new User());
            return "register";
        }

        if (userRepository.findByIco(ico.trim()).isPresent()) {
            model.addAttribute("error", "Tento subjekt je již v systému zaregistrován.");
            model.addAttribute("step", 1);
            model.addAttribute("user", new User());
            return "register";
        }

        AresService.AresResponse aresData = aresService.getCompanyDetails(ico);

        if (aresData != null && aresData.getName() != null) {
            User user = new User();
            user.setIco(aresData.getIco());
            user.setName(aresData.getName());
            user.setAddress(aresData.getAddress());

            model.addAttribute("user", user);
            model.addAttribute("step", 2); // Krok 2: Otevření formuláře pro e-mail, heslo a banku
            model.addAttribute("success", "Firma úspěšně ověřena v registru ARES.");
            return "register";
        } else {
            model.addAttribute("error", "Subjekt s tímto IČO nebyl v registru ARES nalezen.");
            model.addAttribute("step", 1);
            model.addAttribute("user", new User());
            return "register";
        }
    }

    @PostMapping("/register/save")
    public String saveUser(User user, @RequestParam("rawPassword") String rawPassword, Model model) {
        if (user.getEmail() == null || user.getEmail().trim().isEmpty() || rawPassword == null || rawPassword.length() < 6) {
            model.addAttribute("error", "Zadejte platný e-mail a heslo (minimálně 6 znaků).");
            model.addAttribute("step", 2);
            model.addAttribute("user", user);
            return "register";
        }

        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            model.addAttribute("error", "Tento e-mail je již v systému zaregistrován.");
            model.addAttribute("step", 2);
            model.addAttribute("user", user);
            return "register";
        }

        user.setId(null); // zabrání přepsání existujícího uživatele přes formulář
        // Bezpečné zahashování hesla před uložení
        user.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(user);

        return "redirect:/login?registered";
    }
}