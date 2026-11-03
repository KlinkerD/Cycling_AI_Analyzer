package com.cycling.report;

import com.cycling.messaging.contract.DeliveryMessage;
import com.cycling.report.dto.ReportDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportMessageBuilderTest {

    private final ReportMessageBuilder builder = new ReportMessageBuilder(List.of());
    private final ReportDto report = ReportFixtures.report("i1");
    private final ReportCharts charts = new ReportCharts(List.of(
            new ReportCharts.Chart(ReportCharts.SPEED_ALT, "Geschwindigkeit & Höhe", ReportFixtures.png("speed")),
            new ReportCharts.Chart(ReportCharts.HR_ZONES, "Herzfrequenzzonen", ReportFixtures.png("hr"))));

    @Test
    void usesRequestIdAsMessageIdAndActivityAsTitle() {
        DeliveryMessage message = builder.build("r1", report, charts);

        assertThat(message.messageId()).isEqualTo("r1");
        assertThat(message.title()).isEqualTo("🚴 Morgenrunde");
        assertThat(message.subtitle()).isEqualTo("01.11.2026");
        assertThat(message.footer()).isEqualTo("Analysiert am 01.11.2026 10:00 Uhr");
    }

    @Test
    void scoreDecidesColor() {
        assertThat(builder.build("r1", withScore(8), charts).color()).isEqualTo("#4caf50");
        assertThat(builder.build("r1", withScore(5), charts).color()).isEqualTo("#ff9800");
        assertThat(builder.build("r1", withScore(4), charts).color()).isEqualTo("#f44336");
        assertThat(builder.build("r1", withScore(null), charts).color()).isEqualTo("#9e9e9e");
    }

    @Test
    void keyFiguresBecomeFieldsAndMissingValuesAreLeftOut() {
        DeliveryMessage message = builder.build("r1", report, charts);

        assertThat(message.fields()).containsExactly(
                new DeliveryMessage.Field("Score", "8 / 10"),
                new DeliveryMessage.Field("Distanz", "42,5 km"),
                new DeliveryMessage.Field("Dauer", "1h 30m"),
                new DeliveryMessage.Field("Leistung ø", "180 W"));
    }

    @Test
    void aiTextsBecomeColoredSectionsAndBlankOnesAreSkipped() {
        report.analysis().setAnomalies(" ");

        DeliveryMessage message = builder.build("r1", report, charts);

        assertThat(message.sections()).extracting(DeliveryMessage.Section::title)
                .containsExactly("👍 Was gut lief", "📈 Fortschritt", "📝 Fazit");
        assertThat(message.sections().getFirst().text()).isEqualTo("Gleichmaessig getreten");
        assertThat(message.sections().getFirst().color()).isEqualTo("#4caf50");
    }

    @Test
    void chartsBecomeAttachmentsInReportOrder() {
        DeliveryMessage message = builder.build("r1", report, charts);

        assertThat(message.attachments()).extracting(DeliveryMessage.Attachment::name)
                .containsExactly("speedAltChart", "hrZonesChart");
        assertThat(message.attachments().get(1).title()).isEqualTo("Herzfrequenzzonen");
        assertThat(message.attachments().get(1).png()).isEqualTo(ReportFixtures.png("hr"));
    }

    @Test
    void configuredTargetsAreSetAndEmptyMeansDefaultTargets() {
        List<DeliveryMessage.Target> targets = List.of(new DeliveryMessage.Target("DISCORD", "reports"));

        assertThat(new ReportMessageBuilder(targets).build("r1", report, charts).targets()).isEqualTo(targets);
        assertThat(builder.build("r1", report, charts).targets()).isEmpty();
    }

    @Test
    void reportWithoutDatesHasNoSubtitleAndNoFooter() {
        report.analysis().setActivityDate(null);
        report.analysis().setAnalyzedAt(null);

        DeliveryMessage message = builder.build("r1", report, charts);

        assertThat(message.subtitle()).isNull();
        assertThat(message.footer()).isNull();
    }

    private ReportDto withScore(Integer score) {
        ReportDto r = ReportFixtures.report("i1");
        r.analysis().setOverallScore(score);
        r.analysis().setAnalyzedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        return r;
    }
}
