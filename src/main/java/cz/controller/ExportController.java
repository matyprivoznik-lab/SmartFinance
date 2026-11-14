package cz.controller;

import cz.entity.Company;
import cz.entity.User;
import cz.repository.UserRepository;
import cz.service.AccountingExportService;
import cz.service.CompanyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ExportController {

    private final UserRepository userRepository;
    private final CompanyService companyService;
    private final AccountingExportService accountingExportService;

    public ExportController(UserRepository userRepository,
                            CompanyService companyService,
                            AccountingExportService accountingExportService) {
        this.userRepository = userRepository;
        this.companyService = companyService;
        this.accountingExportService = accountingExportService;
    }

    @GetMapping("/export/ucetni.csv")
    public ResponseEntity<byte[]> csv(@AuthenticationPrincipal UserDetails userDetails, HttpServletRequest request) {
        Company company = company(userDetails, request);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        return file(accountingExportService.csv(company), "minifaktura-ucetnictvi.csv", "text/csv");
    }

    @GetMapping("/export/pohoda.xml")
    public ResponseEntity<byte[]> pohoda(@AuthenticationPrincipal UserDetails userDetails, HttpServletRequest request) {
        Company company = company(userDetails, request);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        return file(accountingExportService.pohoda(company), "minifaktura-pohoda.xml", "application/xml");
    }

    private Company company(UserDetails userDetails, HttpServletRequest request) {
        if (userDetails == null) {
            return null;
        }
        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        return companyService.current(user, request);
    }

    private static ResponseEntity<byte[]> file(byte[] body, String name, String type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.parseMediaType(type))
                .body(body);
    }
}
