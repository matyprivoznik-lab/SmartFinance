package cz.controller;

import cz.entity.Product;
import cz.entity.User;
import cz.repository.ProductRepository;
import cz.repository.UserRepository;
import cz.service.CompanyService;
import cz.service.DirectorySearch;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
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
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyService companyService;

    // Zobrazení seznamu produktů
    @GetMapping("/products")
    public String showProducts(@AuthenticationPrincipal UserDetails userDetails,
                                HttpServletRequest request,
                                @RequestParam(value = "q", required = false) String query,
                                @RequestParam(value = "unit", required = false) String unit,
                                Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        String email = userDetails.getUsername();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        List<Product> products = productRepository.findByCompany(companyService.current(currentUser, request));
        String q = DirectorySearch.clean(query);
        String selectedUnit = DirectorySearch.clean(unit);
        Map<Long, String> productSearch = new HashMap<>();
        Set<Long> shownIds = new HashSet<>();
        Set<String> units = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Product stored : products) {
            productSearch.put(stored.getId(), DirectorySearch.productText(stored));
            String unitKey = DirectorySearch.unitKey(stored);
            if (!unitKey.isEmpty()) {
                units.add(unitKey);
            }
            if (DirectorySearch.productVisible(stored, q, selectedUnit)) {
                shownIds.add(stored.getId());
            }
        }

        model.addAttribute("products", products);
        model.addAttribute("productSearch", productSearch);
        model.addAttribute("shownIds", shownIds);
        model.addAttribute("units", units);
        model.addAttribute("q", q);
        model.addAttribute("selectedUnit", selectedUnit);
        model.addAttribute("totalCount", products.size());
        model.addAttribute("shownCount", shownIds.size());
        model.addAttribute("product", new Product());
        model.addAttribute("activePage", "products");

        return "products";
    }

    // Uložení nového produktu nebo položky skladu
    @PostMapping("/products/save")
    public String saveProduct(@AuthenticationPrincipal UserDetails userDetails, HttpServletRequest request, @ModelAttribute Product product) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        String email = userDetails.getUsername();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        if (currentUser != null) {
            product.setId(null);
            product.setUser(currentUser);
            product.setCompany(companyService.current(currentUser, request));
            productRepository.save(product);
        }

        return "redirect:/products";
    }

    // Smazání položky ze skladu
    @PostMapping("/products/delete/{id}")
    public String deleteProduct(@PathVariable("id") Long id, @AuthenticationPrincipal UserDetails userDetails, HttpServletRequest request) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        String email = userDetails.getUsername();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        Product product = productRepository.findById(id).orElse(null);

        // Ověření, že produkt patří přihlášenému uživateli
        if (product != null && currentUser != null && product.getUser() != null
                && product.getUser().getId().equals(currentUser.getId())
                && (product.getCompany() == null || product.getCompany().getId().equals(companyService.current(currentUser, request).getId()))) {
            productRepository.delete(product);
        }

        return "redirect:/products";
    }
}