package com.cycling.delivery;

/** Konfiguration fuer den Discord-Versand (cycling.report.discord.*). */
public record DiscordSettings(boolean enabled, String botToken, String channelId, String apiBaseUrl) {

    public static final String DEFAULT_API_BASE_URL = "https://discord.com/api/v10";
}
