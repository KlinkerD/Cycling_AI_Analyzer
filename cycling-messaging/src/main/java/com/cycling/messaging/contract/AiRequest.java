package com.cycling.messaging.contract;

import java.util.UUID;

/**
 * Auftrag an den ai-service (auf {@code Topics.aiRequest(quelle)}): ein fertiger Prompt, der unveraendert
 * an das Sprachmodell geht. Der ai-service kennt keine Fachlichkeit; Prompt bauen und Antwort
 * auswerten ist Sache des Absenders.
 *
 * @param requestId   Korrelations-ID; kommt unveraendert im {@link AiResult} zurueck
 * @param prompt      vollstaendiger Prompt (Pflicht)
 * @param temperature Sampling-Temperatur, z. B. 0.3 fuer sachliche, 0.7 fuer freiere Texte
 * @param json        true = das Modell soll reines JSON liefern (der Absender parst es selbst)
 */
public record AiRequest(UUID requestId, String prompt, double temperature, boolean json) {
}
