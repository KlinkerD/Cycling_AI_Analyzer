package com.cycling.service;

import com.cycling.dto.DailyRecommendationDto;
import com.cycling.dto.WellnessPointDto;
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
import java.util.Optional;

/**
 * Berechnet die taegliche Trainingsempfehlung (Overview-Karte).
 *
 * <p>Deterministischer Kern: Die Kategorie kommt aus der Form (TSB) des juengsten Wellness-Punkts.
 * Der Ramp-Rate-Waechter (CTL-Aufbau &gt; {@value #RAMP_THRESHOLD} CTL/Woche) stuft die Kategorie um
 * eine Stufe Richtung Erholung herunter. Gemini formuliert nur den Begleittext; faellt der Call aus,
 * wird ein statischer Text pro Kategorie verwendet. Persistiert wird bewusst nur eine Zeile (Upsert).
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("dd.MM.");

    /** Ramp-Rate-Obergrenze in CTL/Woche, ab der nachhaltig aufgebaut werden sollte. */
    private static final double RAMP_THRESHOLD = 6.0;

    private final IntervalsService intervalsService;
    private final GeminiService geminiService;
    private final DailyRecommendationRepository recommendationRepository;
    private final ActivityAnalysisRepository activityRepository;

    public RecommendationService(IntervalsService intervalsService,
                                 GeminiService geminiService,
                                 DailyRecommendationRepository recommendationRepository,
                                 ActivityAnalysisRepository activityRepository) {
        this.intervalsService = intervalsService;
        this.geminiService = geminiService;
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
     * Berechnet die Empfehlung neu (Regeln + Gemini) und ueberschreibt die gespeicherte Zeile.
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
            return DailyRecommendationDto.from(recommendationRepository.save(rec));
        }

        double form = latest.form();
        LocalDate refDate = parseDate(latest.date());
        Double rampRate = computeRampRate(wellness, latest);

        RecommendationCategory base = RecommendationCategory.fromForm(form);
        boolean rampCapped = rampRate != null && rampRate > RAMP_THRESHOLD && base.getLevel() > 0;
        RecommendationCategory category = rampCapped ? base.softer() : base;

        String last7 = buildLast7DaysSummary();
        String text;
        boolean fallbackUsed = false;
        try {
            text = geminiService.phraseRecommendation(
                    category.getLabel(), form, rampRate != null ? rampRate : 0.0, rampCapped, last7);
        } catch (Exception e) {
            log.warn("Gemini-Text nicht verfuegbar, nutze Fallback: {}", e.getMessage());
            text = fallbackText(category, rampCapped);
            fallbackUsed = true;
        }

        rec.setCategory(category);
        rec.setText(text);
        rec.setForm(round(form, 0));
        rec.setRampRate(rampRate != null ? round(rampRate, 1) : null);
        rec.setRampCapped(rampCapped);
        rec.setFallbackUsed(fallbackUsed);
        rec.setRefDate(refDate);

        log.info("Empfehlung: {} (Form {}, Ramp {}, capped={})",
                category, rec.getForm(), rec.getRampRate(), rampCapped);
        return DailyRecommendationDto.from(recommendationRepository.save(rec));
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
