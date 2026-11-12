package cz.controller;

import cz.entity.User;
import cz.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final UserRepository userRepository;

    public SettingsController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public String showSettings(Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Uživatel s e-mailem " + principal.getName() + " nebyl nalezen."));

        model.addAttribute("currentUser", user);
        model.addAttribute("user", user);
        return "settings";
    }

    @PostMapping
    public String updateSettings(@RequestParam(required = false) String name,
                                 @RequestParam(required = false) String ico,
                                 @RequestParam(required = false) String vatNumber,
                                 @RequestParam(required = false) String address,
                                 @RequestParam(required = false) Boolean mainActivity,
                                 @RequestParam(required = false) Double expenseRate,
                                 @RequestParam(value = "vatPayer", required = false) Boolean vatPayer,
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }

        User currentUser = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Uživatel nebyl nalezen."));

        if (name != null && !name.isBlank()) {
            currentUser.setName(name.trim());
        }
        currentUser.setIco(ico);
        currentUser.setVatNumber(vatNumber);
        currentUser.setAddress(address);
        currentUser.setMainActivity(mainActivity == null || mainActivity);
        if (expenseRate != null) {
            currentUser.setExpenseRate(expenseRate);
        }
        currentUser.setVatPayer(Boolean.TRUE.equals(vatPayer));

        userRepository.save(currentUser);

        redirectAttributes.addFlashAttribute("successMessage", "Profil a platební údaje byly úspěšně uloženy.");
        return "redirect:/settings?success";
    }
}
