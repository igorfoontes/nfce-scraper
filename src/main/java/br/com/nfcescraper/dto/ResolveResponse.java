package br.com.nfcescraper.dto;

import java.math.BigDecimal;
import java.util.List;

public record ResolveResponse(
        ResolveStatus status,
        /** true only when the data came from a successful scrape+AI (trustworthy). */
        boolean confirmed,
        BigDecimal total,
        String date,
        String merchant,
        String cnpj,
        List<NfceItem> items,
        String sefazMessage,
        String sourceUrl,
        long elapsedMs
) {
    public static ResolveResponse of(ResolveStatus status, String sefazMessage, String sourceUrl, long elapsedMs) {
        return new ResolveResponse(status, false, null, null, null, null, List.of(), sefazMessage, sourceUrl, elapsedMs);
    }
}
