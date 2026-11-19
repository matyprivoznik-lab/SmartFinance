package cz.config;

import cz.document.DocumentKind;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
@Order(5)
public class DocumentKindSchema implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DocumentKindSchema(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        Integer present = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'invoices'",
                Integer.class);
        if (present == null || present == 0) {
            return;
        }
        String allowed = Arrays.stream(DocumentKind.values())
                .map(kind -> "'" + kind.name() + "'")
                .collect(Collectors.joining(", "));
        jdbcTemplate.execute("alter table invoices drop constraint if exists invoices_document_kind_check");
        jdbcTemplate.execute("alter table invoices add constraint invoices_document_kind_check check (document_kind in (" + allowed + "))");
    }
}
