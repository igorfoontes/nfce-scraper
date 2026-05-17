package br.com.nfcescraper.service;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Per-state NFC-e "online consultation by key" URL templates.
 *
 * The QR online format (version 3) lets the state portal look the nota up live
 * by access key alone — no CSC hash, no captcha. The URL pattern differs per
 * state, so this map grows as each UF is verified empirically.
 *
 * Verified: SP (`?p=<chave>|3|1` → full nota).
 */
public final class StatePortals {

    private static final Map<String, Function<String, String>> NFCE_BY_KEY = Map.of(
            "SP", chave -> "https://www.nfce.fazenda.sp.gov.br/qrcode?p=" + chave + "|3|1"
    );

    private StatePortals() {}

    /** Consultation URL for an NFC-e of the given UF, or empty if not mapped yet. */
    public static Optional<String> nfceConsultUrl(String uf, String chave) {
        var builder = NFCE_BY_KEY.get(uf);
        return builder == null ? Optional.empty() : Optional.of(builder.apply(chave));
    }

    public static boolean supports(String uf) {
        return NFCE_BY_KEY.containsKey(uf);
    }
}
