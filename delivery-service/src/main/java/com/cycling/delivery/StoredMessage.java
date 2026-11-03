package com.cycling.delivery;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Eine angenommene {@code DeliveryMessage}. Der Inhalt (JSON inkl. Bilder) wird nach
 * {@code delivery.retention.payload} geloescht, die Zeile selbst nach {@code delivery.retention.status}.
 */
@Entity
@Table(name = "delivery_message", indexes = @Index(columnList = "receivedAt"))
public class StoredMessage {

    @Id
    private String messageId;

    @Column(nullable = false)
    private String source;

    @Column(length = 500)
    private String title;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    /** Die Nachricht als JSON; null = schon aufgeraeumt. */
    @Lob
    private byte[] payload;

    protected StoredMessage() {
    }

    public StoredMessage(String messageId, String source, String title, LocalDateTime receivedAt, byte[] payload) {
        this.messageId = messageId;
        this.source = source;
        this.title = title != null && title.length() > 500 ? title.substring(0, 500) : title;
        this.receivedAt = receivedAt;
        this.payload = payload;
    }

    public void clearPayload() {
        payload = null;
    }

    public String getMessageId() { return messageId; }
    public String getSource() { return source; }
    public String getTitle() { return title; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public byte[] getPayload() { return payload; }
}
