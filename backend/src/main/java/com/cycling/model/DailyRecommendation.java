package com.cycling.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Die aktuelle Tagesempfehlung. Es existiert bewusst immer nur EINE Zeile (Upsert, keine Historie):
 * der Scheduler bzw. der Refresh-Button ueberschreibt sie. Enthaelt den deterministisch ermittelten
 * Kategorie-Kern plus den (Gemini- oder Fallback-)Begleittext und die treibenden Kennzahlen.
 */
@Entity
@Table(name = "daily_recommendation")
public class DailyRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecommendationCategory category;

    @Column(columnDefinition = "TEXT")
    private String text;

    /** Form (TSB) des juengsten Wellness-Punkts. */
    private Double form;
    /** Ramp Rate = CTL heute - CTL vor 7 Tagen (CTL/Woche). */
    private Double rampRate;
    /** Ob der Ramp-Rate-Waechter die Kategorie heruntergestuft hat. */
    private boolean rampCapped;
    /** Ob der statische Fallback-Text verwendet wurde (Gemini nicht erreichbar). */
    private boolean fallbackUsed;

    /** Tag, auf den sich die Empfehlung bezieht (juengster Wellness-Punkt). */
    private LocalDate refDate;

    /** Zeitpunkt der Berechnung ("Stand"). */
    private LocalDateTime computedAt;

    /** Korrelations-ID des offenen KI-Text-Auftrags; null = kein Text mehr erwartet. */
    private String pendingTextRequestId;

    public DailyRecommendation() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public RecommendationCategory getCategory() {
        return category;
    }

    public void setCategory(RecommendationCategory category) {
        this.category = category;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Double getForm() {
        return form;
    }

    public void setForm(Double form) {
        this.form = form;
    }

    public Double getRampRate() {
        return rampRate;
    }

    public void setRampRate(Double rampRate) {
        this.rampRate = rampRate;
    }

    public boolean isRampCapped() {
        return rampCapped;
    }

    public void setRampCapped(boolean rampCapped) {
        this.rampCapped = rampCapped;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public void setFallbackUsed(boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }

    public LocalDate getRefDate() {
        return refDate;
    }

    public void setRefDate(LocalDate refDate) {
        this.refDate = refDate;
    }

    public LocalDateTime getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(LocalDateTime computedAt) {
        this.computedAt = computedAt;
    }

    public String getPendingTextRequestId() {
        return pendingTextRequestId;
    }

    public void setPendingTextRequestId(String pendingTextRequestId) {
        this.pendingTextRequestId = pendingTextRequestId;
    }
}
