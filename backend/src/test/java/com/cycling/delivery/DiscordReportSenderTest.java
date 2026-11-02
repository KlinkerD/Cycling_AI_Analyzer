package com.cycling.delivery;

import com.cycling.dto.ReportDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import org.springframework.http.HttpStatus;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DiscordReportSenderTest {

    private static final String BASE = "https://discord.test/api/v10";
    private static final String MESSAGES_URL = BASE + "/channels/123/messages";
    private static final String OK_BODY = "{\"id\":\"1\"}";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final List<Duration> sleeps = new ArrayList<>();
    private final DiscordReportSender sender = new DiscordReportSender(
            new DiscordSettings(true, "secret-token", "123", BASE), builder.build(), sleeps::add);

    private final ReportDto report = ReportFixtures.report("i1");
    private final ReportCharts noCharts = new ReportCharts(List.of());

    @Test
    void postsTextMessageWithHeaderAndAiEmbedsUsingBotToken() throws Exception {
        server.expect(requestTo(MESSAGES_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bot secret-token"))
                .andExpect(jsonPath("$.embeds.length()").value(5))
                .andExpect(jsonPath("$.embeds[0].title").value("🚴 Morgenrunde"))
                .andExpect(jsonPath("$.embeds[1].description").value("Gleichmaessig getreten"))
                .andExpect(jsonPath("$.embeds[4].description").value("Solide Einheit"))
                .andExpect(jsonPath("$.allowed_mentions.parse.length()").value(0))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        sender.send(report, noCharts, new ReportDelivery("i1", DeliveryChannel.DISCORD));

        server.verify();
    }

    @Test
    void postsChartsAsSecondMessageWithPngAttachments() throws Exception {
        ReportCharts charts = new ReportCharts(List.of(
                new ReportCharts.Chart(ReportCharts.SPEED_ALT, "Geschwindigkeit & Höhe", ReportFixtures.png("speed")),
                new ReportCharts.Chart(ReportCharts.HR_ZONES, "Herzfrequenzzonen", ReportFixtures.png("hr"))));
        ReportDelivery delivery = new ReportDelivery("i1", DeliveryChannel.DISCORD);

        server.expect(requestTo(MESSAGES_URL))
                .andExpect(jsonPath("$.embeds").exists())
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));
        server.expect(requestTo(MESSAGES_URL))
                .andExpect(header("Authorization", "Bot secret-token"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(containsString("name=\"payload_json\"")))
                .andExpect(content().string(containsString("name=\"files[0]\"; filename=\"speedAltChart.png\"")))
                .andExpect(content().string(containsString("name=\"files[1]\"; filename=\"hrZonesChart.png\"")))
                .andExpect(content().string(containsString("PNG-hr")))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        sender.send(report, charts, delivery);

        server.verify();
        assertThat(delivery.getCompletedParts()).isEqualTo(2);
    }

    @Test
    void retrySendsOnlyChartsWhenTextMessageWasAlreadyPosted() throws Exception {
        ReportCharts charts = new ReportCharts(List.of(
                new ReportCharts.Chart(ReportCharts.SPEED_ALT, "Geschwindigkeit", ReportFixtures.png("speed"))));
        ReportDelivery delivery = new ReportDelivery("i1", DeliveryChannel.DISCORD);
        delivery.setCompletedParts(1);

        server.expect(requestTo(MESSAGES_URL))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        sender.send(report, charts, delivery);

        server.verify();
        assertThat(delivery.getCompletedParts()).isEqualTo(2);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {400, 401, 403, 404})
    void clientErrorsArePermanent(int status) {
        server.expect(requestTo(MESSAGES_URL))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("{\"message\":\"nope\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sender.send(report, noCharts, new ReportDelivery("i1", DeliveryChannel.DISCORD)))
                .isInstanceOfSatisfying(DeliveryException.class, e -> {
                    assertThat(e.isPermanent()).isTrue();
                    assertThat(e.getMessage()).contains(String.valueOf(status));
                });
    }

    @Test
    void networkErrorOnChartsIsTemporaryAndKeepsTextProgress() {
        ReportCharts charts = new ReportCharts(List.of(
                new ReportCharts.Chart(ReportCharts.SPEED_ALT, "Geschwindigkeit", ReportFixtures.png("speed"))));
        ReportDelivery delivery = new ReportDelivery("i1", DeliveryChannel.DISCORD);
        server.expect(requestTo(MESSAGES_URL)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));
        server.expect(requestTo(MESSAGES_URL)).andRespond(withException(new java.net.SocketTimeoutException("timeout")));

        assertThatThrownBy(() -> sender.send(report, charts, delivery))
                .isInstanceOfSatisfying(DeliveryException.class, e -> assertThat(e.isPermanent()).isFalse());
        assertThat(delivery.getCompletedParts()).isEqualTo(1);
    }

    @Test
    void waitsForRetryAfterOnRateLimitAndRetriesOnce() throws Exception {
        server.expect(requestTo(MESSAGES_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"message\":\"You are being rate limited.\",\"retry_after\":1.5,\"global\":false}")
                        .contentType(MediaType.APPLICATION_JSON));
        server.expect(requestTo(MESSAGES_URL)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        ReportDelivery delivery = new ReportDelivery("i1", DeliveryChannel.DISCORD);
        sender.send(report, noCharts, delivery);

        server.verify();
        assertThat(sleeps).containsExactly(Duration.ofMillis(1500));
        assertThat(delivery.getCompletedParts()).isEqualTo(2);
    }

    @Test
    void secondRateLimitIsTemporary() {
        String limited = "{\"retry_after\":0.2}";
        server.expect(requestTo(MESSAGES_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body(limited).contentType(MediaType.APPLICATION_JSON));
        server.expect(requestTo(MESSAGES_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body(limited).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sender.send(report, noCharts, new ReportDelivery("i1", DeliveryChannel.DISCORD)))
                .isInstanceOfSatisfying(DeliveryException.class, e -> assertThat(e.isPermanent()).isFalse());
        assertThat(sleeps).hasSize(1);
    }

    @Test
    void serverErrorIsTemporary() {
        server.expect(requestTo(MESSAGES_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> sender.send(report, noCharts, new ReportDelivery("i1", DeliveryChannel.DISCORD)))
                .isInstanceOfSatisfying(DeliveryException.class, e -> assertThat(e.isPermanent()).isFalse());
    }

    @Test
    void truncatesAiTextsToDiscordEmbedLimit() throws Exception {
        report.analysis().setSummary("x".repeat(5000));
        server.expect(requestTo(MESSAGES_URL))
                .andExpect(jsonPath("$.embeds[4].description").value(org.hamcrest.Matchers.hasLength(4096)))
                .andExpect(jsonPath("$.embeds[4].description").value(org.hamcrest.Matchers.endsWith("…")))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        sender.send(report, noCharts, new ReportDelivery("i1", DeliveryChannel.DISCORD));

        server.verify();
    }

    @Test
    void keepsAllEmbedsTogetherWithinDiscordMessageLimit() throws Exception {
        report.analysis().setStrengths("a".repeat(3000));
        report.analysis().setAnomalies("b".repeat(3000));
        report.analysis().setProgress("c".repeat(200));
        report.analysis().setSummary("d".repeat(3000));
        server.expect(requestTo(MESSAGES_URL))
                .andExpect(request -> {
                    String body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
                    com.fasterxml.jackson.databind.JsonNode embeds = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(body).get("embeds");
                    int total = 0;
                    for (com.fasterxml.jackson.databind.JsonNode e : embeds) {
                        total += e.path("title").asText().length() + e.path("description").asText().length();
                        for (com.fasterxml.jackson.databind.JsonNode f : e.path("fields")) {
                            total += f.get("name").asText().length() + f.get("value").asText().length();
                        }
                    }
                    org.assertj.core.api.Assertions.assertThat(total).isLessThanOrEqualTo(6000);
                    // Kurzer Text bleibt vollstaendig, nur die langen werden gekuerzt
                    org.assertj.core.api.Assertions.assertThat(embeds.get(3).get("description").asText())
                            .isEqualTo("c".repeat(200));
                })
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        sender.send(report, noCharts, new ReportDelivery("i1", DeliveryChannel.DISCORD));

        server.verify();
    }
}
