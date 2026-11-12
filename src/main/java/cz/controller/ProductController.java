package cz.controller;

import cz.entity.Product;
import cz.entity.User;
import cz.repository.ProductRepository;
import cz.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    // Zobrazení seznamu produktů
    @GetMapping("/products")
    public String showProducts(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        String email = userDetails.getUsername();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        List<Product> products = productRepository.findByUser(currentUser);

        model.addAttribute("products", products);
        model.addAttribute("product", new Product());
        model.addAttribute("activePage", "products");

        return "products";
    }

    // Uložení nového produktu nebo položky skladu
    @PostMapping("/products/save")
    public String saveProduct(@AuthenticationPrincipal UserDetails userDetails, @ModelAttribute Product product) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        String email = userDetails.getUsername();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        if (currentUser != null) {
            product.setId(null);
            product.setUser(currentUser);
            productRepository.save(product);
        }

        return "redirect:/products";
    }

    // Smazání položky ze skladu
    @PostMapping("/products/delete/{id}")
    public String deleteProduct(@PathVariable("id") Long id, @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        String email = userDetails.getUsername();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        Product product = productRepository.findById(id).orElse(null);

        // Ověření, že produkt patří přihlášenému uživateli
        if (product != null && currentUser != null && product.getUser() != null
                && product.getUser().getId().equals(currentUser.getId())) {
            productRepository.delete(product);
        }

        return "redirect:/products";
    }
}