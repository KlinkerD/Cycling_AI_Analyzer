package com.cycling.delivery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Gegen echte H2, damit das SQL (REGEXP_LIKE) geprueft ist. */
class LegacyMessageIdCleanupTest {

    private static final String UUID = "6f1c2a4e-0d5b-4c1e-9a7f-2b3c4d5e6f70";

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:cleanup;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("DROP TABLE IF EXISTS delivery");
        jdbc.execute("DROP TABLE IF EXISTS delivery_message");
        jdbc.execute("CREATE TABLE delivery_message (message_id VARCHAR(255) PRIMARY KEY)");
        jdbc.execute("CREATE TABLE delivery (id BIGINT AUTO_INCREMENT PRIMARY KEY, message_id VARCHAR(255))");
        for (String id : new String[]{UUID, "fitness-daily-2026-11-05"}) {
            jdbc.update("INSERT INTO delivery_message (message_id) VALUES (?)", id);
            jdbc.update("INSERT INTO delivery (message_id) VALUES (?)", id);
        }
    }

    @Test
    void removesOnlyNonUuidIds() {
        new LegacyMessageIdCleanup(jdbc).removeNonUuidMessages();

        assertThat(jdbc.queryForList("SELECT message_id FROM delivery_message", String.class)).containsExactly(UUID);
        assertThat(jdbc.queryForList("SELECT message_id FROM delivery", String.class)).containsExactly(UUID);
    }

    @Test
    void missingTablesAreIgnored() {
        jdbc.execute("DROP TABLE delivery");
        jdbc.execute("DROP TABLE delivery_message");

        new LegacyMessageIdCleanup(jdbc).removeNonUuidMessages();
    }
}
