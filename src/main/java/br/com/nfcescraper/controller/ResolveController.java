package br.com.nfcescraper.controller;

import br.com.nfcescraper.dto.BatchRequest;
import br.com.nfcescraper.dto.BatchResponse;
import br.com.nfcescraper.dto.ResolveRequest;
import br.com.nfcescraper.dto.ResolveResponse;
import br.com.nfcescraper.dto.ResolveStatus;
import br.com.nfcescraper.service.ResolveService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class ResolveController {

    private final ResolveService resolveService;

    public ResolveController(ResolveService resolveService) {
        this.resolveService = resolveService;
    }

    /** Resolve a single NFC-e QR. */
    @PostMapping("/resolve")
    public ResponseEntity<ResolveResponse> resolve(@RequestBody ResolveRequest request) {
        return ResponseEntity.ok(resolveService.resolve(request.input()));
    }

    /** Run a batch to measure the real success rate over a sample of QRs. */
    @PostMapping("/batch")
    public ResponseEntity<BatchResponse> batch(@RequestBody BatchRequest request) {
        var inputs = request.inputs() == null ? List.<String>of() : request.inputs();
        List<ResolveResponse> results = inputs.stream()
                .map(resolveService::resolve)
                .toList();

        Map<ResolveStatus, Long> byStatus = results.stream()
                .collect(Collectors.groupingBy(ResolveResponse::status, Collectors.counting()));

        long resolved = byStatus.getOrDefault(ResolveStatus.RESOLVED, 0L);
        int total = results.size();
        double rate = total == 0 ? 0.0 : Math.round((resolved * 10000.0) / total) / 100.0;

        return ResponseEntity.ok(new BatchResponse(total, (int) resolved, rate, byStatus, results));
    }
}
