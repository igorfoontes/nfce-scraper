package br.com.nfcescraper.service;

import br.com.nfcescraper.dto.ResolveResponse;
import br.com.nfcescraper.dto.ResolveStatus;
import org.springframework.stereotype.Service;

@Service
public class ResolveService {

    private final NfcePageFetcher fetcher;
    private final GeminiClient gemini;

    public ResolveService(NfcePageFetcher fetcher, GeminiClient gemini) {
        this.fetcher = fetcher;
        this.gemini = gemini;
    }

    /** Accepts either the QR's URL or a bare 44-digit access key. */
    public ResolveResponse resolve(String input) {
        var start = System.currentTimeMillis();

        var target = toConsultUrl(input);
        if (target.url() == null) {
            return ResolveResponse.of(ResolveStatus.UNSUPPORTED_INPUT, target.reason(), input, elapsed(start));
        }

        var page = fetcher.fetch(target.url());
        var url = page.sourceUrl();

        switch (page.outcome()) {
            case UNSUPPORTED   -> { return ResolveResponse.of(ResolveStatus.UNSUPPORTED_INPUT, page.errorMessage(), url, elapsed(start)); }
            case REJECTED      -> { return ResolveResponse.of(ResolveStatus.SEFAZ_REJECTED, page.errorMessage(), url, elapsed(start)); }
            case SCRAPE_FAILED -> { return ResolveResponse.of(ResolveStatus.SCRAPE_FAILED, page.errorMessage(), url, elapsed(start)); }
            case OK            -> { /* continue */ }
        }

        if (!gemini.isAvailable()) {
            return ResolveResponse.of(ResolveStatus.AI_FAILED, "GEMINI_API_KEY não configurada", url, elapsed(start));
        }
        var data = gemini.extract(page.bodyText());
        if (data == null) {
            return ResolveResponse.of(ResolveStatus.AI_FAILED, "IA não retornou dados válidos", url, elapsed(start));
        }
        if (Boolean.FALSE.equals(data.found())) {
            return ResolveResponse.of(ResolveStatus.SEFAZ_REJECTED, "Página não contém a nota", url, elapsed(start));
        }
        if (!data.hasTotal()) {
            return ResolveResponse.of(ResolveStatus.AI_FAILED, "Total não encontrado na página", url, elapsed(start));
        }

        // confirmed = true: data came from a successful scrape + AI extraction.
        return new ResolveResponse(
                ResolveStatus.RESOLVED, true,
                data.total(), data.date(), data.merchant(), data.cnpj(), data.items(),
                null, url, elapsed(start)
        );
    }

    /** Resolves the raw input to a consultation URL, or explains why it can't. */
    private Target toConsultUrl(String input) {
        if (input == null || input.isBlank()) {
            return Target.no("input vazio");
        }
        var trimmed = input.trim();
        if (trimmed.regionMatches(true, 0, "http", 0, 4)) {
            return Target.url(trimmed);
        }

        var key = AccessKey.parse(trimmed);
        if (key == null) {
            return Target.no("input não é uma URL nem uma chave de 44 dígitos");
        }
        if (!key.isCheckDigitValid()) {
            return Target.no("chave de acesso inválida (dígito verificador)");
        }
        if (key.isNfe()) {
            return Target.no("NF-e modelo 55 não tem consulta pública automática (use valor manual)");
        }
        if (!key.isNfce()) {
            return Target.no("modelo " + key.model() + " não suportado");
        }
        return StatePortals.nfceConsultUrl(key.uf(), key.digits())
                .map(Target::url)
                .orElseGet(() -> Target.no("estado " + key.uf() + " ainda não mapeado para consulta por chave"));
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    private record Target(String url, String reason) {
        static Target url(String u) { return new Target(u, null); }
        static Target no(String r)  { return new Target(null, r); }
    }
}
