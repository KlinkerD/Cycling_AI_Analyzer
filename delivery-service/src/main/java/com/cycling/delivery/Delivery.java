package com.cycling.delivery;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/** Versandstatus einer Nachricht an ein Ziel. Eine Zeile pro (messageId, channel, destination). */
@Entity
@Table(name = "delivery",
        uniqueConstraints = @UniqueConstraint(columnNames = {"messageId", "channel", "destination"}),
        indexes = {@Index(columnList = "messageId"), @Index(columnList = "status, queuedAt")})
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID messageId;

    /** Herkunft laut Topic, z. B. "report" oder "fitness". */
    @Column(nullable = false)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryChannel channel;

    /** Logischer Zielname, z. B. "default" oder "reports"; die Adresse steht in der Konfiguration. */
    @Column(nullable = false)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    private int attempts;

    /** Anzahl bereits erfolgreich gesendeter Teile (Discord: 1 = Text-Nachricht ist raus). */
    private int completedParts;

    @Column(length = 2000)
    private String lastError;

    /** Zeitpunkt des Einreihens; massgeblich fuer das Retry-Zeitfenster und das Aufraeumen. */
    @Column(nullable = false)
    private LocalDateTime queuedAt;

    private LocalDateTime lastAttemptAt;

    protected Delivery() {
    }

    public Delivery(UUID messageId, String source, DeliveryChannel channel, String destination, LocalDateTime queuedAt) {
        this.messageId = messageId;
        this.source = source;
        this.channel = channel;
        this.destination = destination;
        this.queuedAt = queuedAt;
        this.status = DeliveryStatus.PENDING;
    }

    public void markPartCompleted() {
        completedParts++;
    }

    public Long getId() { return id; }
    public UUID getMessageId() { return messageId; }
    public String getSource() { return source; }
    public DeliveryChannel getChannel() { return channel; }
    public String getDestination() { return destination; }
    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public int getCompletedParts() { return completedParts; }
    public void setCompletedParts(int completedParts) { this.completedParts = completedParts; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) {
        this.lastError = lastError != null && lastError.length() > 2000 ? lastError.substring(0, 2000) : lastError;
    }
    public LocalDateTime getQueuedAt() { return queuedAt; }
    public LocalDateTime getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(LocalDateTime lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }
}
