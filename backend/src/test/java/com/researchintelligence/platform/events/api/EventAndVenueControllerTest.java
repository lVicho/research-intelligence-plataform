package com.researchintelligence.platform.events.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.persistence.RoleEntity;
import com.researchintelligence.platform.auth.persistence.UserEntity;
import com.researchintelligence.platform.events.application.EventParticipationService;
import com.researchintelligence.platform.events.application.ScientificEventService;
import com.researchintelligence.platform.publications.api.PublisherController;
import com.researchintelligence.platform.publications.api.PublisherResponse;
import com.researchintelligence.platform.publications.api.VenueController;
import com.researchintelligence.platform.publications.api.VenueRequest;
import com.researchintelligence.platform.publications.api.VenueResponse;
import com.researchintelligence.platform.publications.application.PublisherService;
import com.researchintelligence.platform.publications.application.VenueService;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EventAndVenueControllerTest {

    private static final Instant NOW = Instant.parse("2026-05-17T10:00:00Z");

    @Test
    void createsVenueEndpoint() throws Exception {
        VenueService service = mock(VenueService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new VenueController(service)).build();
        when(service.create(any(VenueRequest.class))).thenReturn(new VenueResponse(
            9L,
            "Revista de IA Clinica Local",
            "RIACL",
            "JOURNAL",
            "2950-1001",
            null,
            null,
            "Espana",
            "https://demo.example.test/revista",
            "Venue description",
            2L,
            true,
            ValidationStatus.VALIDATED,
            NOW,
            NOW,
            1L,
            1L
        ));

        mockMvc.perform(post("/api/venues")
                .contentType("application/json")
                .content("""
                    {
                      "name": "Revista de IA Clinica Local",
                      "shortName": "RIACL",
                      "typeCode": "JOURNAL",
                      "issn": "2950-1001",
                      "country": "Espana",
                      "active": true,
                      "validationStatus": "VALIDATED"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(9))
            .andExpect(jsonPath("$.typeCode").value("JOURNAL"))
            .andExpect(jsonPath("$.validationStatus").value("VALIDATED"));

        verify(service).create(any(VenueRequest.class));
    }

    @Test
    void listsPublishersEndpoint() throws Exception {
        PublisherService service = mock(PublisherService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PublisherController(service)).build();
        when(service.search(0, 20, "ciencia", true)).thenReturn(new PageResponse<>(
            List.of(new PublisherResponse(2L, "Editorial Ciencia Local", "Espana", "https://publisher.example.test", "Publisher description", true, NOW, NOW)),
            0,
            20,
            1,
            1,
            true
        ));

        mockMvc.perform(get("/api/publishers")
                .param("text", "ciencia")
                .param("active", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(2))
            .andExpect(jsonPath("$.content[0].name").value("Editorial Ciencia Local"));

        verify(service).search(0, 20, "ciencia", true);
    }

    @Test
    void createsScientificEventEndpoint() throws Exception {
        ScientificEventService service = mock(ScientificEventService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ScientificEventController(service)).build();
        when(service.create(any(ScientificEventRequest.class))).thenReturn(new ScientificEventResponse(
            4L,
            "Workshop de IA Local en Hospitales 2025",
            "2025",
            "WORKSHOP",
            LocalDate.parse("2025-10-15"),
            LocalDate.parse("2025-10-16"),
            "Madrid",
            "Espana",
            "Hospital Universitario Central",
            "https://event.example.test",
            "Event description",
            "https://event.example.test/evidence",
            null,
            true,
            ValidationStatus.VALIDATED,
            NOW,
            NOW
        ));

        mockMvc.perform(post("/api/scientific-events")
                .contentType("application/json")
                .content("""
                    {
                      "name": "Workshop de IA Local en Hospitales 2025",
                      "edition": "2025",
                      "eventTypeCode": "WORKSHOP",
                      "startDate": "2025-10-15",
                      "endDate": "2025-10-16",
                      "city": "Madrid",
                      "country": "Espana",
                      "validationStatus": "VALIDATED"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(4))
            .andExpect(jsonPath("$.eventTypeCode").value("WORKSHOP"));

        verify(service).create(any(ScientificEventRequest.class));
    }

    @Test
    void submitsEventParticipationEndpoint() throws Exception {
        EventParticipationService service = mock(EventParticipationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new EventParticipationController(service)).build();
        PlatformUserPrincipal principal = principal();
        when(service.submit(33L, principal)).thenReturn(new EventParticipationResponse(
            33L,
            3L,
            "Jornadas de Conservacion de Grandes Felinos 2026",
            7L,
            "Researcher",
            11L,
            "Unit",
            "POSTER",
            "Poster",
            "Description",
            "https://evidence.example.test/poster",
            LocalDate.parse("2026-06-13"),
            null,
            null,
            ValidationStatus.PENDING_VALIDATION,
            NOW,
            "Researcher",
            null,
            null,
            null,
            false,
            false,
            false,
            NOW,
            NOW
        ));

        mockMvc.perform(post("/api/event-participations/33/submit")
                .principal(new UsernamePasswordAuthenticationToken(principal, "password")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(33))
            .andExpect(jsonPath("$.validationStatus").value("PENDING_VALIDATION"));

        verify(service).submit(33L, principal);
    }

    private PlatformUserPrincipal principal() {
        UserEntity user = new UserEntity("researcher@example.test", "Researcher", "{noop}password", true, 7L);
        user.setId(11L);
        user.setRoles(new LinkedHashSet<>(List.of(new RoleEntity("RESEARCHER", "Researcher", "Researcher"))));
        return new PlatformUserPrincipal(user);
    }
}
