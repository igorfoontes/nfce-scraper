package br.com.nfcescraper.service;

import br.com.nfcescraper.dto.NfceData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Extracts structured NFC-e data from the SEFAZ page text using Gemini.
 * Mirrors finance-api's GeminiService (same model family, JSON mode, 429 retry)
 * so the stack stays consistent.
 */
@Service
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent";

    private static final int MAX_RETRIES = 2;
    private static final long MAX_RETRY_DELAY_MS = 120_000;
    private static final Pattern RETRY_DELAY_JSON = Pattern.compile("\"retryDelay\"\\s*:\\s*\"([0-9.]+)s\"");
    private static final Pattern RETRY_DELAY_MSG = Pattern.compile("retry in ([0-9.]+)s");

    @Value("${gemini.api-key:}")
    private String apiKey;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    public NfceData extract(String pageText) {
        var prompt = buildPrompt(pageText);
        var requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json")
        );

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                var response = restClient.post()
                        .uri(GEMINI_URL + "?key=" + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(Map.class);

                var candidates = (List<?>) response.get("candidates");
                var candidate = (Map<?, ?>) candidates.get(0);
                var content = (Map<?, ?>) candidate.get("content");
                var parts = (List<?>) content.get("parts");
                var part = (Map<?, ?>) parts.get(0);
                var text = (String) part.get("text");

                log.info("Gemini raw response: {}", text);
                return objectMapper.readValue(text, NfceData.class);

            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    long delayMs = parseRetryDelay(e.getResponseBodyAsString());
                    log.warn("Gemini 429 – tentativa {}/{}, retry em {}ms", attempt + 1, MAX_RETRIES + 1, delayMs);
                    if (attempt < MAX_RETRIES && delayMs <= MAX_RETRY_DELAY_MS) {
                        try { Thread.sleep(delayMs); }
                        catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                    } else {
                        break;
                    }
                } else {
                    log.error("Gemini extraction failed: {}", e.getMessage());
                    return null;
                }
            } catch (Exception e) {
                log.error("Gemini extraction failed: {}", e.getMessage());
                return null;
            }
        }
        log.error("Gemini rate limit não resolvido após {} tentativas", MAX_RETRIES + 1);
        return null;
    }

    private String buildPrompt(String pageText) {
        if (pageText.length() > 60_000) pageText = pageText.substring(0, 60_000);
        return """
            Você recebe o TEXTO de uma página pública de consulta de NFC-e
            (Nota Fiscal de Consumidor Eletrônica) de QUALQUER SEFAZ estadual
            brasileira (SP, RJ, MG, RS, PR, SC, BA, etc.). O layout varia por
            estado; identifique os dados independentemente do formato. Extraia
            e retorne um JSON com:

            - found: boolean (true se a página contém os dados da nota; false se
              for página de erro, "QR Code inválido", captcha ou sem a nota)
            - total: number (valor total a pagar da nota em reais, ponto decimal,
              sem "R$"; null se não encontrar)
            - date: string "YYYY-MM-DD" (data de emissão), null se não encontrar
            - merchant: string (nome/razão social do estabelecimento emitente),
              null se não encontrar
            - cnpj: string (apenas dígitos do CNPJ do emitente), null se não achar
            - items: array de objetos { description: string, amount: number }
              com os produtos da nota (amount positivo em reais); [] se não houver

            Regras:
            - Retorne APENAS o JSON, sem markdown, sem explicação
            - Se found=false, total=null, items=[]
            - O total é o valor final da nota — rótulos variam por estado:
              "Valor a pagar", "Valor total", "Valor total da nota",
              "Valor Total da Nota Fiscal", "Total R$", "Valor líquido", etc.
              Use o valor final cobrado do consumidor (após descontos)
            - Valores sempre positivos, ponto como separador decimal
            - Datas em qualquer formato (DD/MM/AAAA, etc.) → converter p/ YYYY-MM-DD

            Texto da página:
            """ + pageText;
    }

    private long parseRetryDelay(String body) {
        try {
            var m = RETRY_DELAY_JSON.matcher(body);
            if (m.find()) return (long) (Double.parseDouble(m.group(1)) * 1000) + 1000;
            m = RETRY_DELAY_MSG.matcher(body);
            if (m.find()) return (long) (Double.parseDouble(m.group(1)) * 1000) + 1000;
        } catch (Exception ignored) {}
        return 15_000;
    }
}
