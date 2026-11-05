package com.cycling.delivery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** UUID-messageId gegen echte H2: gespeichert als Text, alle Abfragen damit lauffaehig. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:delivery-persistence;DB_CLOSE_DELAY=-1",
        "mqtt.broker-url=tcp://localhost:1",
        "mqtt.client-id=delivery-persistence-test"
})
class PersistenceTypesTest {

    @Autowired
    private MessageRepository messages;
    @Autowired
    private DeliveryRepository deliveries;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void messageIdIsUuidStoredAsText() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        messages.save(new StoredMessage(id, "report", "Titel", now, new byte[]{1}));
        deliveries.save(new Delivery(id, "report", DeliveryChannel.DISCORD, "default", now));

        assertThat(messages.existsById(id)).isTrue();
        assertThat(messages.findById(id)).isPresent();
        assertThat(deliveries.findByMessageIdAndStatus(id, DeliveryStatus.PENDING)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT message_id FROM delivery_message WHERE message_id = ?", String.class,
                id.toString())).isEqualTo(id.toString());
    }
}
