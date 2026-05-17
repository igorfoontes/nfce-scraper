package br.com.nfcescraper.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Fetches the NFC-e public consultation page using the QR code's OWN URL.
 *
 * The QR content of any NFC-e is already the full state-SEFAZ consultation
 * URL (SP, RJ, MG, ...). Following it as-is makes this multi-state by
 * construction — the AI layer absorbs each state's HTML differences. We only
 * (a) restrict to government fiscal domains (SSRF guard) and (b) send the URL
 * with literal '|' (SEFAZ rejects a percent-encoded `p`).
 */
@Service
public class NfcePageFetcher {

    private static final Logger log = LoggerFactory.getLogger(NfcePageFetcher.class);

    /** Real rejection sentences (shown only on error, not template tokens). */
    private static final List<String> REJECTION_MARKERS = List.of(
            "problemas na consulta via qr code",
            "formato de qr-code não suportado", "formato de qr-code nao suportado",
            "qr code inválido", "qr code invalido",
            "hash qr code inválido", "hash qr code invalido",
            "chave de acesso inválida", "chave de acesso invalida",
            "não foi localizada", "nao foi localizada",
            "nfc-e não encontrada", "nfc-e nao encontrada",
            "documento inexistente", "nota fiscal inexistente"
    );

    /** Words that indicate the page actually rendered the nota. */
    private static final List<String> CONTENT_MARKERS = List.of(
            "valor a pagar", "valor total", "valor total da nota",
            "informações da nfc-e", "informacoes da nfc-e",
            "número", "emitente", "consumidor"
    );

    @Value("${sefaz.timeout-ms:20000}")
    private int timeoutMs;

    @Value("${sefaz.user-agent}")
    private String userAgent;

    public FetchedPage fetch(String qrUrl) {
        if (qrUrl == null || qrUrl.isBlank()) {
            return FetchedPage.unsupported(null, "input vazio");
        }
        var url = qrUrl.trim();
        if (!url.regionMatches(true, 0, "http", 0, 4)) {
            return FetchedPage.unsupported(url, "input não é uma URL (QR de NFC-e deve conter a URL completa)");
        }

        var host = hostOf(url);
        if (host == null || !isGovFiscalHost(host)) {
            return FetchedPage.unsupported(url, "domínio não permitido: " + host);
        }

        try {
            @SuppressWarnings("deprecation")
            var u = new URL(url); // lenient: tolerates literal '|', unlike URI
            var http = (HttpURLConnection) u.openConnection();
            http.setInstanceFollowRedirects(true);
            http.setConnectTimeout(timeoutMs);
            http.setReadTimeout(timeoutMs);
            http.setRequestProperty("User-Agent", userAgent);
            http.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            http.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9");

            int status = http.getResponseCode();
            InputStream stream = status >= 400 ? http.getErrorStream() : http.getInputStream();
            String html = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            http.disconnect();

            Document doc = Jsoup.parse(html, url);
            String visible = doc.body() != null ? doc.body().text() : doc.text();
            String haystack = (visible + " " + html).toLowerCase();

            boolean hasContent = CONTENT_MARKERS.stream().anyMatch(haystack::contains);
            String rejection = firstRejection(html, haystack);

            // Only treat as rejected when the error is present AND the nota
            // content is not — avoids false positives from template tokens.
            if (rejection != null && !hasContent) {
                log.info("SEFAZ rejeitou (status {}): {}", status, rejection);
                return FetchedPage.rejected(url, status, rejection);
            }
            if (visible == null || visible.isBlank() || !hasContent) {
                return FetchedPage.scrapeFailed(url, status, "Página sem conteúdo de nota legível");
            }
            if (visible.length() > 60_000) visible = visible.substring(0, 60_000);
            return FetchedPage.ok(url, status, visible);

        } catch (Exception e) {
            log.warn("Falha ao buscar página NFC-e ({}): {}", host, e.getMessage());
            return FetchedPage.scrapeFailed(url, 0, "Falha de rede: " + e.getMessage());
        }
    }

    private String hostOf(String url) {
        try {
            // URI rejects raw '|', so trim the query before parsing the host.
            var q = url.indexOf('?');
            var base = q >= 0 ? url.substring(0, q) : url;
            return URI.create(base).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    /** Allow only Brazilian government fiscal hosts (SSRF guard). */
    private boolean isGovFiscalHost(String host) {
        var h = host.toLowerCase();
        if (!h.endsWith(".gov.br")) return false;
        return h.contains("fazenda") || h.contains("sefaz") || h.contains("nfce") || h.contains("dfe");
    }

    private String firstRejection(String html, String haystack) {
        for (var marker : REJECTION_MARKERS) {
            int idx = haystack.indexOf(marker);
            if (idx >= 0) {
                int from = Math.min(idx, html.length());
                return html.substring(from, Math.min(from + 160, html.length()))
                        .replaceAll("<[^>]*>", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
            }
        }
        return null;
    }

    public enum Outcome { OK, REJECTED, SCRAPE_FAILED, UNSUPPORTED }

    public record FetchedPage(
            Outcome outcome, String sourceUrl, int httpStatus,
            String errorMessage, String bodyText
    ) {
        static FetchedPage ok(String url, int s, String body) {
            return new FetchedPage(Outcome.OK, url, s, null, body);
        }
        static FetchedPage rejected(String url, int s, String msg) {
            return new FetchedPage(Outcome.REJECTED, url, s, msg, null);
        }
        static FetchedPage scrapeFailed(String url, int s, String msg) {
            return new FetchedPage(Outcome.SCRAPE_FAILED, url, s, msg, null);
        }
        static FetchedPage unsupported(String url, String msg) {
            return new FetchedPage(Outcome.UNSUPPORTED, url, 0, msg, null);
        }
    }
}
