package br.com.nfcescraper.service;

import java.util.Map;

/**
 * The 44-digit NF-e/NFC-e access key (chave de acesso). Its structure is
 * deterministic, so state, document model and validity come from the digits
 * alone — no network needed.
 *
 * Layout: cUF(2) AAMM(4) CNPJ(14) mod(2) serie(3) nNF(9) tpEmis(1) cNF(8) cDV(1)
 */
public record AccessKey(String digits) {

    public static final String MODEL_NFE = "55";   // electronic invoice (B2B)
    public static final String MODEL_NFCE = "65";  // consumer receipt

    private static final Map<String, String> UF_BY_CUF = Map.ofEntries(
            Map.entry("11", "RO"), Map.entry("12", "AC"), Map.entry("13", "AM"),
            Map.entry("14", "RR"), Map.entry("15", "PA"), Map.entry("16", "AP"),
            Map.entry("17", "TO"), Map.entry("21", "MA"), Map.entry("22", "PI"),
            Map.entry("23", "CE"), Map.entry("24", "RN"), Map.entry("25", "PB"),
            Map.entry("26", "PE"), Map.entry("27", "AL"), Map.entry("28", "SE"),
            Map.entry("29", "BA"), Map.entry("31", "MG"), Map.entry("32", "ES"),
            Map.entry("33", "RJ"), Map.entry("35", "SP"), Map.entry("41", "PR"),
            Map.entry("42", "SC"), Map.entry("43", "RS"), Map.entry("50", "MS"),
            Map.entry("51", "MT"), Map.entry("52", "GO"), Map.entry("53", "DF")
    );

    /** Parses a raw input (may carry spaces/dots); returns null if not 44 digits. */
    public static AccessKey parse(String raw) {
        if (raw == null) return null;
        var d = raw.replaceAll("\\D", "");
        return d.length() == 44 ? new AccessKey(d) : null;
    }

    public String cUF()   { return digits.substring(0, 2); }
    public String uf()    { return UF_BY_CUF.getOrDefault(cUF(), "?"); }
    public String model() { return digits.substring(20, 22); }
    public boolean isNfce() { return MODEL_NFCE.equals(model()); }
    public boolean isNfe()  { return MODEL_NFE.equals(model()); }

    /** Validates the check digit (cDV) via modulo 11 over the first 43 digits. */
    public boolean isCheckDigitValid() {
        int sum = 0, weight = 2;
        for (int i = 42; i >= 0; i--) {
            sum += (digits.charAt(i) - '0') * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        int mod = sum % 11;
        int dv = (mod == 0 || mod == 1) ? 0 : 11 - mod;
        return dv == (digits.charAt(43) - '0');
    }
}
