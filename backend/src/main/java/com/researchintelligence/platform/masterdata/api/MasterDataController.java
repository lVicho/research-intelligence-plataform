package com.researchintelligence.platform.masterdata.api;

import com.researchintelligence.platform.masterdata.application.MasterDataService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/master-data")
public class MasterDataController {

    private final MasterDataService service;

    public MasterDataController(MasterDataService service) {
        this.service = service;
    }

    @GetMapping("/publication-types")
    public List<MasterDataItemResponse> publicationTypes() {
        return service.findPublicationTypes();
    }

    @GetMapping("/publication-statuses")
    public List<MasterDataItemResponse> publicationStatuses() {
        return service.findPublicationStatuses();
    }

    @GetMapping("/venue-types")
    public List<MasterDataItemResponse> venueTypes() {
        return service.findVenueTypes();
    }

    @GetMapping("/event-types")
    public List<MasterDataItemResponse> eventTypes() {
        return service.findEventTypes();
    }

    @GetMapping("/event-participation-types")
    public List<MasterDataItemResponse> eventParticipationTypes() {
        return service.findEventParticipationTypes();
    }
}
