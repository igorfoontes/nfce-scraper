package br.com.nfcescraper.dto;

public enum ResolveStatus {
    /** Total successfully extracted from the SEFAZ page. */
    RESOLVED,
    /** SEFAZ returned the page but rejected the QR (invalid/expired hash, not found). */
    SEFAZ_REJECTED,
    /** Could not reach SEFAZ or the page had no usable content. */
    SCRAPE_FAILED,
    /** Page was fetched but the AI could not extract a valid total. */
    AI_FAILED,
    /** Input is not a supported SP NFC-e QR URL. */
    UNSUPPORTED_INPUT
}
