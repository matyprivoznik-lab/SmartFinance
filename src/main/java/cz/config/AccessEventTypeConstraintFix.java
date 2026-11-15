package cz.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class AccessEventTypeConstraintFix implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public AccessEventTypeConstraintFix(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        jdbcTemplate.execute("ALTER TABLE access_events DROP CONSTRAINT IF EXISTS access_events_type_check");
    }
}
