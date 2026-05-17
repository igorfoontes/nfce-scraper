package br.com.nfcescraper.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/** Data the AI extracts from the SEFAZ NFC-e page. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NfceData(
        @JsonProperty("found") Boolean found,
        @JsonProperty("total") BigDecimal total,
        @JsonProperty("date") String date,
        @JsonProperty("merchant") String merchant,
        @JsonProperty("cnpj") String cnpj,
        @JsonProperty("items") List<NfceItem> items
) {
    public NfceData {
        items = items != null ? items : List.of();
    }

    public boolean hasTotal() {
        return total != null && total.signum() > 0;
    }
}
