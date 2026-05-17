package br.com.nfcescraper.dto;

/** `input` may be the full SP NFC-e QR URL or the raw `p=` payload. */
public record ResolveRequest(String input) {}
