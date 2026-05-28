package com.researchintelligence.platform.ingestion.api;

import com.researchintelligence.platform.ingestion.application.PublicationCsvIngestionService;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ingestion")
public class PublicationCsvIngestionController {

    private final PublicationCsvIngestionService service;

    public PublicationCsvIngestionController(PublicationCsvIngestionService service) {
        this.service = service;
    }

    @PostMapping(path = "/publications/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PublicationCsvIngestionReportResponse ingestPublicationsCsv(@RequestPart("file") MultipartFile file) throws IOException {
        return service.ingest(file.getInputStream());
    }
}
