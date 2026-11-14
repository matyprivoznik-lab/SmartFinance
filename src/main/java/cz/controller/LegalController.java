package cz.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LegalController {

    @GetMapping("/vop")
    public String terms() {
        return "legal/vop";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "legal/privacy";
    }
}
