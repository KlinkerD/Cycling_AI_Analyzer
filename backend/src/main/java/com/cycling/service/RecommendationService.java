package com.cycling.service;

import com.cycling.dto.DailyRecommendationDto;
import com.cycling.dto.WellnessPointDto;
import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.Topics;
import com.cycling.messaging.contract.RecommendationTextRequest;
import com.cycling.messaging.contract.RecommendationTextResult;
import com.cycling.model.ActivityAnalysis;
import com.cycling.model.DailyRecommendation;
import com.cycling.model.RecommendationCategory;
import com.cycling.repository.ActivityAnalysisRepository;
import com.cycling.repository.DailyRecommendationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Berechnet die taegliche Trainingsempfehlung (Overview-Karte).
 *
 * <p>Deterministischer Kern: Die Kategorie kommt aus der Form (TSB) des juengsten Wellness-Punkts.
 * Der Ramp-Rate-Waechter (CTL-Aufbau &gt; {@value #RAMP_THRESHOLD} CTL/Woche) stuft die Kategorie um
 * eine Stufe Richtung Erholung herunter. Gespeichert wird sofort ein statischer Text pro Kategorie;
 * den formulierten Begleittext liefert der ai-service per MQTT nach ({@link #applyText}).
 * Persistiert wird bewusst nur eine Zeile (Upsert).
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("dd.MM.");

    /** Ramp-Rate-Obergrenze in CTL/Woche, ab der nachhaltig aufgebaut werden sollte. */
    private static final double RAMP_THRESHOLD = 6.0;

    private final IntervalsService intervalsService;
    private final MessagePublisher publisher;
    private final DailyRecommendationRepository recommendationRepository;
    private final ActivityAnalysisRepository activityRepository;

    public RecommendationService(IntervalsService intervalsService,
                                 MessagePublisher publisher,
                                 DailyRecommendationRepository recommendationRepository,
                                 ActivityAnalysisRepository activityRepository) {
        this.intervalsService = intervalsService;
        this.publisher = publisher;
        this.recommendationRepository = recommendationRepository;
        this.activityRepository = activityRepository;
    }

    /**
     * Gibt die gespeicherte Empfehlung zurueck. Existiert noch keine (Kaltstart), wird sie
     * einmalig synchron berechnet.
     */
    public DailyRecommendationDto getCurrent() {
        return recommendationRepository.findTopByOrderByIdAsc()
                .map(DailyRecommendationDto::from)
                .orElseGet(this::computeAndStore);
    }

    /**
     * Berechnet die Empfehlung neu (Regeln + Fallback-Text), ueberschreibt die gespeicherte Zeile
     * und beauftragt den ai-service mit dem Begleittext.
     * Wird vom Scheduler und vom manuellen Refresh-Button genutzt – ein einziger Code-Pfad.
     */
    public DailyRecommendationDto computeAndStore() {
        log.info("Berechne Tagesempfehlung neu...");

        List<WellnessPointDto> wellness = loadWellness();
        WellnessPointDto latest = latestWithCtl(wellness);

        DailyRecommendation rec = recommendationRepository.findTopByOrderByIdAsc()
                .orElseGet(DailyRecommendation::new);
        rec.setComputedAt(LocalDateTime.now());

        if (latest == null || latest.form() == null) {
            log.info("Keine CTL/ATL-Daten – Empfehlung = INSUFFICIENT_DATA");
            rec.setCategory(RecommendationCategory.INSUFFICIENT_DATA);
            rec.setText("Es liegen noch keine ausreichenden Fitness-Daten (CTL/ATL) vor, "
                    + "um eine Empfehlung zu geben. Sobald Trainings erfasst sind, erscheint hier ein Vorschlag.");
            rec.setForm(null);
            rec.setRampRate(null);
            rec.setRampCapped(false);
            rec.setFallbackUsed(false);
            rec.setRefDate(null);
            rec.setPendingTextRequestId(null);
            return DailyRecommendationDto.from(recommendationRepository.save(rec));
        }

        double form = latest.form();
        LocalDate refDate = parseDate(latest.date());
        Double rampRate = computeRampRate(wellness, latest);

        RecommendationCategory base = RecommendationCategory.fromForm(form);
        boolean rampCapped = rampRate != null && rampRate > RAMP_THRESHOLD && base.getLevel() > 0;
        RecommendationCategory category = rampCapped ? base.softer() : base;

        rec.setCategory(category);
        rec.setText(fallbackText(category, rampCapped));
        rec.setForm(round(form, 0));
        rec.setRampRate(rampRate != null ? round(rampRate, 1) : null);
        rec.setRampCapped(rampCapped);
        rec.setFallbackUsed(true);
        rec.setRefDate(refDate);
        String requestId = UUID.randomUUID().toString();
        rec.setPendingTextRequestId(requestId);
        // Erst speichern, dann senden: eine schnelle Antwort muss die Request-ID schon vorfinden.
        rec = recommendationRepository.save(rec);

        log.info("Empfehlung: {} (Form {}, Ramp {}, capped={})",
                category, rec.getForm(), rec.getRampRate(), rampCapped);
        try {
            publisher.publish(Topics.AI_RECOMMENDATION_REQUEST, new RecommendationTextRequest(
                    requestId, category.getLabel(), form, rampRate != null ? rampRate : 0.0, rampCapped,
                    buildLast7DaysSummary()));
        } catch (RuntimeException e) {
            log.warn("KI-Text nicht angefragt, Fallback bleibt: {}", e.getMessage());
            rec.setPendingTextRequestId(null);
            rec = recommendationRepository.save(rec);
        }
        // Nicht erneut speichern: der KI-Text kann inzwischen schon eingetroffen sein.
        return DailyRecommendationDto.from(rec);
    }

    /**
     * Uebernimmt den KI-Text, sofern er zur aktuell gespeicherten Empfehlung gehoert. Antworten auf
     * aeltere Berechnungen werden ignoriert; bei Fehler bleibt der Fallback-Text stehen.
     */
    public void applyText(RecommendationTextResult result) {
        DailyRecommendation rec = recommendationRepository.findTopByOrderByIdAsc().orElse(null);
        if (rec == null || !result.requestId().equals(rec.getPendingTextRequestId())) {
            log.debug("Veralteter Empfehlungstext {} ignoriert", result.requestId());
            return;
        }
        if (result.success() && result.text() != null && !result.text().isBlank()) {
            rec.setText(result.text());
            rec.setFallbackUsed(false);
        } else {
            log.warn("KI-Text nicht verfuegbar, Fallback bleibt: {}", result.error());
        }
        rec.setPendingTextRequestId(null);
        recommendationRepository.save(rec);
    }

    // ---- Helfer --------------------------------------------------------------

    private List<WellnessPointDto> loadWellness() {
        // 21 Tage Fenster: genug, um sicher einen ~7-Tage-alten CTL-Wert zu finden.
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(21);
        try {
            return intervalsService.getWellness(from.format(DATE_FMT), to.format(DATE_FMT));
        } catch (Exception e) {
            log.warn("Wellness-Daten fuer Empfehlung nicht ladbar: {}", e.getMessage());
            return List.of();
        }
    }

    /** Juengster Punkt mit vorhandenem CTL (Liste ist aufsteigend nach Datum sortiert). */
    private WellnessPointDto latestWithCtl(List<WellnessPointDto> wellness) {
        WellnessPointDto latest = null;
        for (WellnessPointDto p : wellness) {
            if (p.ctl() != null) latest = p;
        }
        return latest;
    }

    /** Ramp Rate = CTL des juengsten Punkts - CTL des Punkts, der ~7 Tage davor liegt. */
    private Double computeRampRate(List<WellnessPointDto> wellness, WellnessPointDto latest) {
        if (latest.ctl() == null) return null;
        LocalDate target = parseDate(latest.date()).minusDays(7);
        WellnessPointDto ref = null;
        long best = Long.MAX_VALUE;
        for (WellnessPointDto p : wellness) {
            if (p.ctl() == null) continue;
            long diff = Math.abs(ChronoUnit.DAYS.between(parseDate(p.date()), target));
            if (diff < best) {
                best = diff;
                ref = p;
            }
        }
        if (ref == null || best > 10) return null; // kein brauchbarer Referenzpunkt
        return latest.ctl() - ref.ctl();
    }

    private String buildLast7DaysSummary() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<ActivityAnalysis> recent = activityRepository.findByActivityDateAfterOrderByActivityDateDesc(since);
        if (recent.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (ActivityAnalysis a : recent) {
            sb.append("- ");
            sb.append(a.getActivityDate() != null ? a.getActivityDate().format(DAY_FMT) : "?.?.");
            sb.append(" ").append(a.getActivityName());
            if (a.getTrainingLoad() != null) {
                sb.append(String.format(Locale.GERMAN, ", %.0f TSS", a.getTrainingLoad()));
            }
            if (a.getIntensityFactor() != null) {
                sb.append(String.format(Locale.GERMAN, ", IF %.2f", a.getIntensityFactor()));
            }
            if (a.getDistanceKm() != null) {
                sb.append(String.format(Locale.GERMAN, ", %.0f km", a.getDistanceKm()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String fallbackText(RecommendationCategory category, boolean rampCapped) {
        String base = switch (category) {
            case HARD_INTERVALS -> "Deine Form ist sehr frisch – heute ist eine harte Einheit mit Intervallen oder einem intensiven Block gut machbar.";
            case TEMPO -> "Du bist frisch genug für einen intensiven Reiz – Tempo- bzw. Sweet-Spot-Arbeit passt heute.";
            case ENDURANCE -> "Deine Form ist neutral – heute eignet sich eine ruhige Grundlageneinheit im GA1/GA2-Bereich.";
            case EASY -> "Du bist leicht ermüdet – halte es heute locker und fahre nur regenerativ.";
            case REST -> "Deine Form deutet auf deutliche Ermüdung hin – heute besser Pause oder maximal lockeres Ausrollen.";
            case INSUFFICIENT_DATA -> "Es liegen noch keine ausreichenden Fitness-Daten vor.";
        };
        if (rampCapped && category != RecommendationCategory.INSUFFICIENT_DATA) {
            base += " Du baust gerade schnell auf – heute bewusst etwas zurückhaltender.";
        }
        return base;
    }

    private LocalDate parseDate(String date) {
        return LocalDate.parse(date.length() > 10 ? date.substring(0, 10) : date);
    }

    private double round(double v, int digits) {
        double f = Math.pow(10, digits);
        return Math.round(v * f) / f;
    }
}
