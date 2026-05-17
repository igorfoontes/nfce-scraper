package br.com.nfcescraper.dto;

import java.util.List;
import java.util.Map;

/** Aggregate result of a batch run — used to measure the real success rate. */
public record BatchResponse(
        int total,
        int resolved,
        double successRate,
        Map<ResolveStatus, Long> byStatus,
        List<ResolveResponse> results
) {}
