package com.researchintelligence.platform.masterdata.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchintelligence.platform.masterdata.application.MasterDataService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MasterDataControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-05-17T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-05-17T10:05:00Z");

    private MasterDataService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(MasterDataService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MasterDataController(service)).build();
    }

    @Test
    void exposesPublicationTypes() throws Exception {
        when(service.findPublicationTypes()).thenReturn(List.of(item(1L, "JOURNAL_ARTICLE", "Art\u00edculo", 10)));

        mockMvc.perform(get("/api/master-data/publication-types"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].code").value("JOURNAL_ARTICLE"))
            .andExpect(jsonPath("$[0].labelEs").value("Art\u00edculo"))
            .andExpect(jsonPath("$[0].active").value(true))
            .andExpect(jsonPath("$[0].sortOrder").value(10));

        verify(service).findPublicationTypes();
    }

    @Test
    void exposesPublicationStatuses() throws Exception {
        when(service.findPublicationStatuses()).thenReturn(List.of(item(1L, "DRAFT", "Borrador", 10)));

        mockMvc.perform(get("/api/master-data/publication-statuses"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("DRAFT"))
            .andExpect(jsonPath("$[0].labelEs").value("Borrador"));

        verify(service).findPublicationStatuses();
    }

    @Test
    void exposesVenueTypes() throws Exception {
        when(service.findVenueTypes()).thenReturn(List.of(item(1L, "JOURNAL", "Revista", 10)));

        mockMvc.perform(get("/api/master-data/venue-types"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("JOURNAL"))
            .andExpect(jsonPath("$[0].labelEs").value("Revista"));

        verify(service).findVenueTypes();
    }

    @Test
    void exposesEventTypes() throws Exception {
        when(service.findEventTypes()).thenReturn(List.of(item(1L, "CONFERENCE", "Congreso", 10)));

        mockMvc.perform(get("/api/master-data/event-types"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("CONFERENCE"))
            .andExpect(jsonPath("$[0].labelEs").value("Congreso"));

        verify(service).findEventTypes();
    }

    @Test
    void exposesEventParticipationTypes() throws Exception {
        when(service.findEventParticipationTypes()).thenReturn(List.of(item(2L, "POSTER", "P\u00f3ster", 20)));

        mockMvc.perform(get("/api/master-data/event-participation-types"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("POSTER"))
            .andExpect(jsonPath("$[0].labelEs").value("P\u00f3ster"));

        verify(service).findEventParticipationTypes();
    }

    private MasterDataItemResponse item(Long id, String code, String labelEs, int sortOrder) {
        return new MasterDataItemResponse(
            id,
            code,
            labelEs,
            "Descripci\u00f3n",
            true,
            sortOrder,
            CREATED_AT,
            UPDATED_AT
        );
    }
}
