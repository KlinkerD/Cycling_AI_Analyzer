package com.cycling.delivery;

import com.cycling.messaging.contract.DeliveryMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasLength;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DiscordChannelSenderTest {

    private static final String BASE = "https://discord.test/api/v10";
    private static final String DEFAULT_URL = BASE + "/channels/123/messages";
    private static final String REPORTS_URL = BASE + "/channels/456/messages";
    private static final String OK_BODY = "{\"id\":\"1\"}";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final List<Duration> sleeps = new ArrayList<>();
    private final DiscordChannelSender sender = new DiscordChannelSender(
            new DiscordSettings(true, "secret-token", Map.of("default", "123", "reports", "456"), BASE),
            builder.build(), sleeps::add);

    private final DeliveryMessage message = MessageFixtures.message("m1");

    private static Delivery delivery(String destination) {
        return new Delivery(MessageFixtures.id("m1"), "report", DeliveryChannel.DISCORD, destination, LocalDateTime.now());
    }

    @Test
    void postsHeaderAndSectionEmbedsToDestinationChannelUsingBotToken() throws Exception {
        server.expect(requestTo(REPORTS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bot secret-token"))
                .andExpect(jsonPath("$.embeds.length()").value(5))
                .andExpect(jsonPath("$.embeds[0].title").value("🚴 Morgenrunde"))
                .andExpect(jsonPath("$.embeds[0].description").value("01.11.2026"))
                .andExpect(jsonPath("$.embeds[0].color").value(0x4CAF50))
                .andExpect(jsonPath("$.embeds[0].fields[0].name").value("Score"))
                .andExpect(jsonPath("$.embeds[0].fields[0].value").value("8 / 10"))
                .andExpect(jsonPath("$.embeds[0].fields[0].inline").value(true))
                .andExpect(jsonPath("$.embeds[0].footer.text").value("Analysiert am 01.11.2026 10:00 Uhr"))
                .andExpect(jsonPath("$.embeds[1].title").value("👍 Was gut lief"))
                .andExpect(jsonPath("$.embeds[1].description").value("Gleichmaessig getreten"))
                .andExpect(jsonPath("$.embeds[1].color").value(0x4CAF50))
                .andExpect(jsonPath("$.embeds[4].description").value("Solide Einheit"))
                .andExpect(jsonPath("$.allowed_mentions.parse.length()").value(0))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        sender.send(message, "reports", delivery("reports"));

        server.verify();
    }

    @Test
    void invalidColorFallsBackToGrey() throws Exception {
        DeliveryMessage noColor = new DeliveryMessage(MessageFixtures.id("m1"), "Hallo", null, "rot", null, null, null, null, null);
        server.expect(requestTo(DEFAULT_URL))
                .andExpect(jsonPath("$.embeds.length()").value(1))
                .andExpect(jsonPath("$.embeds[0].color").value(0x9E9E9E))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        sender.send(noColor, "default", delivery("default"));

        server.verify();
    }

    @Test
    void postsAttachmentsAsSecondMessageWithPngFiles() throws Exception {
        DeliveryMessage withCharts = MessageFixtures.withAttachments(message,
                MessageFixtures.attachment("speedAltChart", "Geschwindigkeit & Höhe"),
                MessageFixtures.attachment("hrZonesChart", "Herzfrequenzzonen"));
        Delivery delivery = delivery("default");

        server.expect(requestTo(DEFAULT_URL))
                .andExpect(jsonPath("$.embeds").exists())
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));
        server.expect(requestTo(DEFAULT_URL))
                .andExpect(header("Authorization", "Bot secret-token"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(containsString("name=\"payload_json\"")))
                .andExpect(content().string(containsString("name=\"files[0]\"; filename=\"speedAltChart.png\"")))
                .andExpect(content().string(containsString("name=\"files[1]\"; filename=\"hrZonesChart.png\"")))
                .andExpect(content().string(containsString("PNG-hrZonesChart")))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        sender.send(withCharts, "default", delivery);

        server.verify();
        assertThat(delivery.getCompletedParts()).isEqualTo(2);
    }

    @Test
    void moreThanTenAttachmentsAreSplitIntoSeveralMessages() throws Exception {
        DeliveryMessage many = MessageFixtures.withAttachments(message, IntStream.range(0, 12)
                .mapToObj(i -> MessageFixtures.attachment("chart" + i, "Chart " + i))
                .toArray(DeliveryMessage.Attachment[]::new));
        Delivery delivery = delivery("default");

        server.expect(requestTo(DEFAULT_URL)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));
        server.expect(requestTo(DEFAULT_URL))
                .andExpect(content().string(containsString("filename=\"chart9.png\"")))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));
        server.expect(requestTo(DEFAULT_URL))
                .andExpect(content().string(containsString("name=\"files[0]\"; filename=\"chart10.png\"")))
                .andExpect(content().string(containsString("filename=\"chart11.png\"")))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        sender.send(many, "default", delivery);

        server.verify();
        assertThat(delivery.getCompletedParts()).isEqualTo(3);
    }

    @Test
    void retrySendsOnlyAttachmentsWhenTextMessageWasAlreadyPosted() throws Exception {
        DeliveryMessage withCharts = MessageFixtures.withAttachments(message,
                MessageFixtures.attachment("speedAltChart", "Geschwindigkeit"));
        Delivery delivery = delivery("default");
        delivery.setCompletedParts(1);

        server.expect(requestTo(DEFAULT_URL))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        sender.send(withCharts, "default", delivery);

        server.verify();
        assertThat(delivery.getCompletedParts()).isEqualTo(2);
    }

    @Test
    void unknownDestinationIsPermanent() {
        assertThatThrownBy(() -> sender.send(message, "alerts", delivery("alerts")))
                .isInstanceOfSatisfying(DeliveryException.class, e -> {
                    assertThat(e.isPermanent()).isTrue();
                    assertThat(e.getMessage()).contains("alerts");
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404})
    void clientErrorsArePermanent(int status) {
        server.expect(requestTo(DEFAULT_URL))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("{\"message\":\"nope\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sender.send(message, "default", delivery("default")))
                .isInstanceOfSatisfying(DeliveryException.class, e -> {
                    assertThat(e.isPermanent()).isTrue();
                    assertThat(e.getMessage()).contains(String.valueOf(status));
                });
    }

    @Test
    void networkErrorOnAttachmentsIsTemporaryAndKeepsTextProgress() {
        DeliveryMessage withCharts = MessageFixtures.withAttachments(message,
                MessageFixtures.attachment("speedAltChart", "Geschwindigkeit"));
        Delivery delivery = delivery("default");
        server.expect(requestTo(DEFAULT_URL)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));
        server.expect(requestTo(DEFAULT_URL)).andRespond(withException(new java.net.SocketTimeoutException("timeout")));

        assertThatThrownBy(() -> sender.send(withCharts, "default", delivery))
                .isInstanceOfSatisfying(DeliveryException.class, e -> assertThat(e.isPermanent()).isFalse());
        assertThat(delivery.getCompletedParts()).isEqualTo(1);
    }

    @Test
    void waitsForRetryAfterOnRateLimitAndRetriesOnce() throws Exception {
        server.expect(requestTo(DEFAULT_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"message\":\"You are being rate limited.\",\"retry_after\":1.5,\"global\":false}")
                        .contentType(MediaType.APPLICATION_JSON));
        server.expect(requestTo(DEFAULT_URL)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        Delivery delivery = delivery("default");
        sender.send(message, "default", delivery);

        server.verify();
        assertThat(sleeps).containsExactly(Duration.ofMillis(1500));
        assertThat(delivery.getCompletedParts()).isEqualTo(1);
    }

    @Test
    void secondRateLimitIsTemporary() {
        String limited = "{\"retry_after\":0.2}";
        server.expect(requestTo(DEFAULT_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body(limited).contentType(MediaType.APPLICATION_JSON));
        server.expect(requestTo(DEFAULT_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body(limited).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sender.send(message, "default", delivery("default")))
                .isInstanceOfSatisfying(DeliveryException.class, e -> assertThat(e.isPermanent()).isFalse());
        assertThat(sleeps).hasSize(1);
    }

    @Test
    void serverErrorIsTemporary() {
        server.expect(requestTo(DEFAULT_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> sender.send(message, "default", delivery("default")))
                .isInstanceOfSatisfying(DeliveryException.class, e -> assertThat(e.isPermanent()).isFalse());
    }

    @Test
    void truncatesSectionTextsToDiscordEmbedLimit() throws Exception {
        DeliveryMessage longSummary = MessageFixtures.withSections(message,
                MessageFixtures.sections("a", "b", "c", "x".repeat(5000)));
        server.expect(requestTo(DEFAULT_URL))
                .andExpect(jsonPath("$.embeds[4].description").value(hasLength(4096)))
                .andExpect(jsonPath("$.embeds[4].description").value(endsWith("…")))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        sender.send(longSummary, "default", delivery("default"));

        server.verify();
    }

    @Test
    void keepsAllEmbedsTogetherWithinDiscordMessageLimit() throws Exception {
        DeliveryMessage longTexts = MessageFixtures.withSections(message,
                MessageFixtures.sections("a".repeat(3000), "b".repeat(3000), "c".repeat(200), "d".repeat(3000)));
        server.expect(requestTo(DEFAULT_URL))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    JsonNode embeds = new ObjectMapper().readTree(body).get("embeds");
                    int total = 0;
                    for (JsonNode e : embeds) {
                        total += e.path("title").asText().length() + e.path("description").asText().length()
                                + e.path("footer").path("text").asText().length();
                        for (JsonNode f : e.path("fields")) {
                            total += f.get("name").asText().length() + f.get("value").asText().length();
                        }
                    }
                    assertThat(total).isLessThanOrEqualTo(6000);
                    // Kurzer Text bleibt vollstaendig, nur die langen werden gekuerzt
                    assertThat(embeds.get(3).get("description").asText()).isEqualTo("c".repeat(200));
                })
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        sender.send(longTexts, "default", delivery("default"));

        server.verify();
    }
}
