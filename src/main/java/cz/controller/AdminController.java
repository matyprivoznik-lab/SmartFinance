package cz.controller;

import cz.entity.AccessEvent;
import cz.entity.User;
import cz.repository.AccessEventRepository;
import cz.repository.UserRepository;
import cz.service.AccountModerationService;
import cz.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final AccessEventRepository accessEventRepository;
    private final AccountModerationService accountModerationService;
    private final AuditService auditService;

    public AdminController(UserRepository userRepository,
                           AccessEventRepository accessEventRepository,
                           AccountModerationService accountModerationService,
                           AuditService auditService) {
        this.userRepository = userRepository;
        this.accessEventRepository = accessEventRepository;
        this.accountModerationService = accountModerationService;
        this.auditService = auditService;
    }

    @GetMapping
    public String users(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("currentEmail", authentication == null ? "" : authentication.getName());
        model.addAttribute("activePage", "admin");
        return "admin/users";
    }

    @GetMapping("/events")
    public String events(Model model) {
        model.addAttribute("events", accessEventRepository.findTop200ByOrderByCreatedAtDesc());
        model.addAttribute("activePage", "admin");
        return "admin/events";
    }

    @PostMapping("/users/{id}/block")
    public String block(@PathVariable Long id,
                        Authentication authentication,
                        HttpServletRequest request,
                        RedirectAttributes redirectAttributes) {
        return moderate(id, true, authentication, request, redirectAttributes);
    }

    @PostMapping("/users/{id}/unblock")
    public String unblock(@PathVariable Long id,
                          Authentication authentication,
                          HttpServletRequest request,
                          RedirectAttributes redirectAttributes) {
        return moderate(id, false, authentication, request, redirectAttributes);
    }

    private String moderate(Long id,
                            boolean blocked,
                            Authentication authentication,
                            HttpServletRequest request,
                            RedirectAttributes redirectAttributes) {
        User actor = authentication == null ? null : userRepository.findByEmail(authentication.getName()).orElse(null);
        User target = userRepository.findById(id).orElse(null);
        boolean changed = accountModerationService.setBlocked(actor, id, blocked);
        if (!changed) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tento účet zablokovat nejde.");
            return "redirect:/admin";
        }
        auditService.record(request, target, target.getEmail(),
                blocked ? AccessEvent.Type.USER_BLOCKED : AccessEvent.Type.USER_UNBLOCKED,
                actor != null ? "Provedl " + actor.getEmail() : null);
        redirectAttributes.addFlashAttribute("successMessage", blocked ? "Účet je zablokovaný." : "Účet je znovu povolený.");
        return "redirect:/admin";
    }
}
