package cz.controller;

import cz.billing.CheckoutSession;
import cz.billing.GatewayEvent;
import cz.billing.SubscriptionPlan;
import cz.entity.Subscription;
import cz.entity.User;
import cz.repository.UserRepository;
import cz.service.BillingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
@RequestMapping("/billing")
public class BillingController {

    private final BillingService billingService;
    private final UserRepository userRepository;

    public BillingController(BillingService billingService, UserRepository userRepository) {
        this.billingService = billingService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String showBilling(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        Subscription subscription = billingService.getOrCreateSubscription(user);
        boolean gatewayReady = billingService.getPaymentGateway().isConfigured();

        model.addAttribute("subscription", subscription);
        model.addAttribute("plans", new SubscriptionPlan[]{SubscriptionPlan.MONTHLY, SubscriptionPlan.YEARLY});
        model.addAttribute("gatewayReady", gatewayReady);
        model.addAttribute("providerName", billingService.getPaymentGateway().providerName());
        model.addAttribute("activePage", "billing");
        model.addAttribute("isActive", billingService.isActive(user));
        return "billing";
    }

    @PostMapping("/checkout")
    public String startCheckout(@RequestParam("plan") String planName,
                                @AuthenticationPrincipal UserDetails userDetails,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        SubscriptionPlan plan;
        try {
            plan = SubscriptionPlan.valueOf(planName);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Neplatný plán.");
            return "redirect:/billing";
        }

        String baseUrl = ServletUriComponentsBuilder.fromContextPath(request)
                .build()
                .toUriString();
        boolean stripeReady = billingService.getPaymentGateway().isConfigured();
        String successUrl = stripeReady
                ? baseUrl + "/billing/success?session_id={CHECKOUT_SESSION_ID}"
                : baseUrl + "/billing/success";
        String cancelUrl = baseUrl + "/billing/cancel";

        try {
            CheckoutSession session = billingService.startCheckout(user, plan, successUrl, cancelUrl);
            return "redirect:" + session.redirectUrl();
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/billing";
        }
    }

    @GetMapping("/success")
    public String checkoutSuccess(@RequestParam(value = "session_id", required = false) String sessionId,
                                  @RequestParam(value = "demo", required = false) String demo,
                                  @RequestParam(value = "plan", required = false) String planName,
                                  @RequestParam(value = "checkout_id", required = false) String checkoutId,
                                  @AuthenticationPrincipal UserDetails userDetails,
                                  RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        if ("1".equals(demo) && planName != null) {
            try {
                billingService.activateDemoCheckout(user, SubscriptionPlan.valueOf(planName), checkoutId);
                redirectAttributes.addFlashAttribute("successMessage",
                        "Demo předplatné aktivováno (bez Stripe klíčů).");
            } catch (RuntimeException e) {
                redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            }
        } else {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Platba přijata. Předplatné se aktivuje po potvrzení z platební brány.");
        }

        return "redirect:/billing";
    }

    @GetMapping("/cancel")
    public String checkoutCancel(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", "Platba byla zrušena.");
        return "redirect:/billing";
    }

    @PostMapping("/cancel-subscription")
    public String cancelSubscription(@AuthenticationPrincipal UserDetails userDetails,
                                     RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }
        billingService.cancelSubscription(user);
        redirectAttributes.addFlashAttribute("successMessage", "Předplatné bylo zrušeno.");
        return "redirect:/billing";
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(@RequestBody String payload,
                                          @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        try {
            var event = billingService.getPaymentGateway().parseWebhook(payload, signature == null ? "" : signature);
            event.ifPresent(billingService::handleGatewayEvent);
            return ResponseEntity.ok("ok");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
