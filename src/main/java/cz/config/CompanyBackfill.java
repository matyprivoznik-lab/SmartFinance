package cz.config;

import cz.document.PublicLinks;
import cz.entity.Client;
import cz.entity.Company;
import cz.entity.Invoice;
import cz.entity.Product;
import cz.entity.RecurringTemplate;
import cz.entity.User;
import cz.repository.ClientRepository;
import cz.repository.CompanyRepository;
import cz.repository.InvoiceRepository;
import cz.repository.ProductRepository;
import cz.repository.RecurringTemplateRepository;
import cz.repository.UserRepository;
import cz.service.CompanyService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(90)
public class CompanyBackfill implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CompanyService companyService;
    private final CompanyRepository companyRepository;
    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;
    private final RecurringTemplateRepository recurringTemplateRepository;

    public CompanyBackfill(UserRepository userRepository,
                           CompanyService companyService,
                           CompanyRepository companyRepository,
                           InvoiceRepository invoiceRepository,
                           ClientRepository clientRepository,
                           ProductRepository productRepository,
                           RecurringTemplateRepository recurringTemplateRepository) {
        this.userRepository = userRepository;
        this.companyService = companyService;
        this.companyRepository = companyRepository;
        this.invoiceRepository = invoiceRepository;
        this.clientRepository = clientRepository;
        this.productRepository = productRepository;
        this.recurringTemplateRepository = recurringTemplateRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        for (User user : userRepository.findAll()) {
            Company company = companyRepository.findByOwnerOrderByIdAsc(user).stream().findFirst().orElseGet(() -> companyService.ensure(user));
            for (Invoice invoice : invoiceRepository.findByUser(user)) {
                boolean changed = false;
                if (invoice.getCompany() == null) {
                    invoice.setCompany(company);
                    changed = true;
                }
                if (invoice.getPublicToken() == null || invoice.getPublicToken().isBlank()) {
                    PublicLinks.ensure(invoice);
                    changed = true;
                }
                if (changed) {
                    invoiceRepository.save(invoice);
                }
            }
            for (Client client : clientRepository.findByUser(user)) {
                if (client.getCompany() == null) {
                    client.setCompany(company);
                    clientRepository.save(client);
                }
            }
            for (Product product : productRepository.findByUser(user)) {
                if (product.getCompany() == null) {
                    product.setCompany(company);
                    productRepository.save(product);
                }
            }
            for (RecurringTemplate template : recurringTemplateRepository.findByUserOrderByNextIssueDateAsc(user)) {
                if (template.getCompany() == null) {
                    template.setCompany(company);
                    recurringTemplateRepository.save(template);
                }
            }
        }
    }
}
