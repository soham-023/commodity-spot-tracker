package io.github.soham023.spottracker.api;

import io.github.soham023.spottracker.ingest.IngestionRun;
import io.github.soham023.spottracker.ingest.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ingest")
@Tag(name = "Ingestion")
public class IngestController {

    private final IngestionService ingestion;

    public IngestController(IngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @PostMapping("/run")
    @Operation(summary = "Run every enabled price source now", description =
            "Same work as the daily scheduled job. Each source reports OK, FAILED or SKIPPED independently.")
    public IngestionRun run() {
        return ingestion.runAll();
    }
}
