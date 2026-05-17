package br.com.nfcescraper.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NfceItem(
        @JsonProperty("description") String description,
        @JsonProperty("amount") BigDecimal amount
) {}
