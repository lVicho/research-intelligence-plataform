package com.researchintelligence.platform.auth.application;

import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.auth.api.MeActivityDetailResponse;
import com.researchintelligence.platform.auth.api.MeActivityResponse;
import com.researchintelligence.platform.auth.api.MeDashboardResponse;
import com.researchintelligence.platform.auth.api.MePublicationYearCountResponse;
import com.researchintelligence.platform.auth.api.MeResearcherProfileResponse;
import com.researchintelligence.platform.auth.api.MeTopicCountResponse;
import com.researchintelligence.platform.events.persistence.EventParticipationEntity;
import com.researchintelligence.platform.events.persistence.EventParticipationRepository;
import com.researchintelligence.platform.auth.persistence.ResearcherActivityRow;
import com.researchintelligence.platform.auth.persistence.ResearcherWorkspaceRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import com.researchintelligence.platform.validation.persistence.ValidationInboxRepository;
import com.researchintelligence.platform.validation.persistence.ValidationItemRow;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ResearcherWorkspaceService {

    private final ResearcherWorkspaceRepository workspaceRepository;
    private final ValidationInboxRepository validationInboxRepository;
    private final ResearcherRepository researcherRepository;
    private final ResearcherAffiliationRepository affiliationRepository;
    private final PublicationRepository publicationRepository;
    private final EventParticipationRepository eventParticipationRepository;
    private final ActivityAuditService auditService;

    public ResearcherWorkspaceService(
        ResearcherWorkspaceRepository workspaceRepository,
        ValidationInboxRepository validationInboxRepository,
        ResearcherRepository researcherRepository,
        ResearcherAffiliationRepository affiliationRepository,
        PublicationRepository publicationRepository,
        EventParticipationRepository eventParticipationRepository,
        ActivityAuditService auditService
    ) {
        this.workspaceRepository = workspaceRepository;
        this.validationInboxRepository = validationInboxRepository;
        this.researcherRepository = researcherRepository;
        this.affiliationRepository = affiliationRepository;
        this.publicationRepository = publicationRepository;
        this.eventParticipationRepository = eventParticipationRepository;
        this.auditService = auditService;
    }

    public MeDashboardResponse dashboard(PlatformUserPrincipal user) {
        Long researcherId = requireLinkedResearcher(user);
        ResearcherActivityRow profileRow = workspaceRepository.findOwnedActivity(researcherId, ValidationEntityType.RESEARCHER, researcherId)
            .orElseThrow(() -> new ResourceNotFoundException("Researcher", researcherId));
        Map<ValidationStatus, Long> counts = statusCounts(researcherId);
        List<String> reminders = new ArrayList<>(dataQualityReminders(profileRow));
        workspaceRepository.recentActivities(researcherId, 100).stream()
            .flatMap(row -> dataQualityReminders(row).stream())
            .distinct()
            .limit(8)
            .forEach(reminders::add);

        return new MeDashboardResponse(
            new MeResearcherProfileResponse(
                researcherId,
                profileRow.title(),
                displayNameFromSubtitle(profileRow.subtitle()),
                profileRow.email(),
                profileRow.orcid(),
                Boolean.TRUE.equals(profileRow.active()),
                profileRow.researchUnitName()
            ),
            counts.getOrDefault(ValidationStatus.VALIDATED, 0L),
            counts.getOrDefault(ValidationStatus.DRAFT, 0L),
            counts.getOrDefault(ValidationStatus.PENDING_VALIDATION, 0L),
            counts.getOrDefault(ValidationStatus.CHANGES_REQUESTED, 0L),
            counts.getOrDefault(ValidationStatus.REJECTED, 0L),
            workspaceRepository.publicationsByYear(researcherId).stream()
                .map(row -> new MePublicationYearCountResponse((Integer) row[0], (Long) row[1]))
                .toList(),
            workspaceRepository.mainTopics(researcherId, 8).stream()
                .map(row -> new MeTopicCountResponse((Long) row[0], (String) row[1], (Long) row[2]))
                .toList(),
            workspaceRepository.recentActivities(researcherId, 6).stream()
                .map(this::toActivityResponse)
                .toList(),
            reminders.stream().distinct().toList()
        );
    }

    public PageResponse<MeActivityResponse> activities(
        PlatformUserPrincipal user,
        ValidationStatus status,
        ValidationEntityType type,
        String text,
        int page,
        int size
    ) {
        Long researcherId = requireLinkedResearcher(user);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<ResearcherActivityRow> rows = workspaceRepository.activities(researcherId, status, type, text, safePage, safeSize);
        return PageResponse.from(rows.map(this::toActivityResponse));
    }

    public MeActivityDetailResponse activityDetail(PlatformUserPrincipal user, ValidationEntityType type, Long entityId) {
        Long researcherId = user.researcherId();
        if (researcherId != null) {
            return workspaceRepository.findOwnedActivity(researcherId, type, entityId)
                .map(this::toActivityDetailResponse)
                .orElseGet(() -> {
                    if (isAdmin(user)) {
                        return adminActivityDetail(type, entityId);
                    }
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Activity does not belong to the current researcher.");
                });
        }
        if (isAdmin(user)) {
            return adminActivityDetail(type, entityId);
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current user is not linked to a researcher.");
    }

    @Transactional
    public MeActivityDetailResponse submitActivity(PlatformUserPrincipal user, ValidationEntityType type, Long entityId) {
        Long researcherId = requireLinkedResearcher(user);
        ResearcherActivityRow row = workspaceRepository.findOwnedActivity(researcherId, type, entityId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Activity does not belong to the current researcher."));
        if (!canSubmit(row.validationStatus())) {
            throw new BusinessRuleException("Only draft activities or activities with requested changes can be submitted.");
        }
        switch (type) {
            case RESEARCHER -> {
                ResearcherEntity entity = researcherRepository.findById(entityId)
                    .orElseThrow(() -> new ResourceNotFoundException("Researcher", entityId));
                ValidationStatus previousStatus = entity.getValidationStatus();
                entity.setValidationStatus(ValidationStatus.PENDING_VALIDATION);
                entity.setValidatedByUserId(null);
                entity.setValidatedAt(null);
                auditService.recordStatusChange(type, entityId, previousStatus, ValidationStatus.PENDING_VALIDATION, null);
            }
            case RESEARCHER_AFFILIATION -> {
                ResearcherAffiliationEntity entity = affiliationRepository.findByIdAndResearcherId(entityId, researcherId)
                    .orElseThrow(() -> new ResourceNotFoundException("Researcher affiliation", entityId));
                ValidationStatus previousStatus = entity.getValidationStatus();
                entity.setValidationStatus(ValidationStatus.PENDING_VALIDATION);
                entity.setValidatedByUserId(null);
                entity.setValidatedAt(null);
                auditService.recordStatusChange(type, entityId, previousStatus, ValidationStatus.PENDING_VALIDATION, null);
            }
            case PUBLICATION -> {
                PublicationEntity entity = publicationRepository.findById(entityId)
                    .orElseThrow(() -> new ResourceNotFoundException("Publication", entityId));
                ValidationStatus previousStatus = entity.getValidationStatus();
                entity.setValidationStatus(ValidationStatus.PENDING_VALIDATION);
                entity.setValidatedByUserId(null);
                entity.setValidatedAt(null);
                auditService.recordStatusChange(type, entityId, previousStatus, ValidationStatus.PENDING_VALIDATION, null);
            }
            case EVENT_PARTICIPATION -> {
                EventParticipationEntity entity = eventParticipationRepository.findByIdAndResearcherId(entityId, researcherId)
                    .orElseThrow(() -> new ResourceNotFoundException("EventParticipation", entityId));
                ValidationStatus previousStatus = entity.getValidationStatus();
                entity.setValidationStatus(ValidationStatus.PENDING_VALIDATION);
                entity.setSubmittedAt(java.time.Instant.now());
                entity.setValidatedAt(null);
                auditService.recordStatusChange(type, entityId, previousStatus, ValidationStatus.PENDING_VALIDATION, null);
            }
            case RESEARCH_UNIT, SCIENTIFIC_EVENT, VENUE, PUBLISHER, TOPIC ->
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This entity type is not a researcher-owned activity.");
        }
        return workspaceRepository.findOwnedActivity(researcherId, type, entityId)
            .map(this::toActivityDetailResponse)
            .orElseThrow(() -> new ResourceNotFoundException(type.name(), entityId));
    }

    private MeActivityDetailResponse adminActivityDetail(ValidationEntityType type, Long entityId) {
        ValidationItemRow row = validationInboxRepository.findByEntity(type, entityId)
            .orElseThrow(() -> new ResourceNotFoundException(type.name(), entityId));
        ResearcherActivityRow activity = new ResearcherActivityRow(
            row.entityType(),
            row.entityId(),
            row.title(),
            row.subtitle(),
            row.researcherId(),
            row.researcherName(),
            row.researchUnitId(),
            row.researchUnitName(),
            row.submittedAt(),
            row.validationStatus(),
            row.validationComment(),
            row.validatedByUserId(),
            row.validatedBy(),
            row.validatedAt(),
            row.primaryType(),
            row.secondaryStatus(),
            row.yearValue(),
            row.doi(),
            row.sourceValue(),
            row.email(),
            row.orcid(),
            row.active(),
            row.roleValue(),
            row.primaryAffiliation(),
            row.startDate(),
            row.endDate(),
            row.abstractPresent(),
            row.internalAuthorCount(),
            row.topicCount()
        );
        return toActivityDetailResponse(activity);
    }

    private Map<ValidationStatus, Long> statusCounts(Long researcherId) {
        Map<ValidationStatus, Long> counts = new EnumMap<>(ValidationStatus.class);
        workspaceRepository.countActivitiesByStatus(researcherId)
            .forEach(row -> counts.put((ValidationStatus) row[0], (Long) row[1]));
        return counts;
    }

    private MeActivityResponse toActivityResponse(ResearcherActivityRow row) {
        return new MeActivityResponse(
            row.entityType(),
            row.entityId(),
            row.title(),
            row.subtitle(),
            row.researcherId(),
            row.researcherName(),
            row.researchUnitId(),
            row.researchUnitName(),
            row.submittedAt(),
            row.validationStatus(),
            row.validationComment(),
            summaryFields(row),
            dataQualityReminders(row),
            canEdit(row.validationStatus()),
            canSubmit(row.validationStatus())
        );
    }

    private MeActivityDetailResponse toActivityDetailResponse(ResearcherActivityRow row) {
        return new MeActivityDetailResponse(
            row.entityType(),
            row.entityId(),
            row.title(),
            row.subtitle(),
            row.researcherId(),
            row.researcherName(),
            row.researchUnitId(),
            row.researchUnitName(),
            row.submittedAt(),
            row.validationStatus(),
            row.validationComment(),
            row.validatedBy(),
            row.validatedAt(),
            detailFields(row),
            warnings(row),
            dataQualityReminders(row),
            canEdit(row.validationStatus()),
            canSubmit(row.validationStatus())
        );
    }

    private Map<String, String> summaryFields(ResearcherActivityRow row) {
        Map<String, String> fields = new LinkedHashMap<>();
        switch (row.entityType()) {
            case RESEARCHER -> {
                putIfPresent(fields, "Email", row.email());
                putIfPresent(fields, "ORCID", row.orcid());
            }
            case RESEARCHER_AFFILIATION -> {
                putIfPresent(fields, "Unidad", row.researchUnitName());
                putIfPresent(fields, "Rol", row.roleValue());
            }
            case PUBLICATION -> {
                putIfPresent(fields, "Tipo", row.primaryType());
                putIfPresent(fields, "Estado", row.secondaryStatus());
                putIfPresent(fields, "A\u00f1o", row.yearValue() == null ? null : row.yearValue().toString());
            }
            case EVENT_PARTICIPATION -> {
                putIfPresent(fields, "Tipo", row.primaryType());
                putIfPresent(fields, "Evento", row.sourceValue());
                putIfPresent(fields, "A\u00f1o", row.yearValue() == null ? null : row.yearValue().toString());
            }
            case RESEARCH_UNIT -> putIfPresent(fields, "Unidad", row.title());
            case SCIENTIFIC_EVENT -> putIfPresent(fields, "Evento", row.title());
            case VENUE -> putIfPresent(fields, "Canal", row.title());
            case PUBLISHER -> putIfPresent(fields, "Editorial", row.title());
            case TOPIC -> putIfPresent(fields, "Tema", row.title());
        }
        return fields;
    }

    private Map<String, String> detailFields(ResearcherActivityRow row) {
        Map<String, String> fields = new LinkedHashMap<>(summaryFields(row));
        switch (row.entityType()) {
            case RESEARCHER -> {
                putIfPresent(fields, "Nombre", row.title());
                putIfPresent(fields, "Afiliaci\u00f3n principal", row.researchUnitName());
            }
            case RESEARCHER_AFFILIATION -> {
                putIfPresent(fields, "Investigador", row.researcherName());
                putIfPresent(fields, "Tipo de afiliaci\u00f3n", row.primaryType());
                putIfPresent(fields, "Principal", booleanLabel(row.primaryAffiliation()));
                putIfPresent(fields, "Fecha de inicio", row.startDate() == null ? null : row.startDate().toString());
                putIfPresent(fields, "Fecha de fin", row.endDate() == null ? null : row.endDate().toString());
            }
            case PUBLICATION -> {
                putIfPresent(fields, "T\u00edtulo", row.title());
                putIfPresent(fields, "DOI", row.doi());
                putIfPresent(fields, "Fuente", row.sourceValue());
                putIfPresent(fields, "Autores internos", row.internalAuthorCount() == null ? null : row.internalAuthorCount().toString());
                putIfPresent(fields, "Temas", row.topicCount() == null ? null : row.topicCount().toString());
            }
            case EVENT_PARTICIPATION -> {
                putIfPresent(fields, "T\u00edtulo", row.title());
                putIfPresent(fields, "Evento", row.sourceValue());
                putIfPresent(fields, "Tipo de evento", row.secondaryStatus());
                putIfPresent(fields, "Unidad", row.researchUnitName());
                putIfPresent(fields, "Fecha", row.startDate() == null ? null : row.startDate().toString());
                putIfPresent(fields, "Organizador", row.roleValue());
            }
            case RESEARCH_UNIT, SCIENTIFIC_EVENT, VENUE, PUBLISHER, TOPIC -> putIfPresent(fields, "Nombre", row.title());
        }
        return fields;
    }

    private List<String> warnings(ResearcherActivityRow row) {
        List<String> warnings = new ArrayList<>();
        if (row.entityType() == ValidationEntityType.RESEARCHER && Boolean.FALSE.equals(row.active())) {
            warnings.add("Tu perfil est\u00e1 marcado como inactivo.");
        }
        if (row.entityType() == ValidationEntityType.PUBLICATION && row.yearValue() != null && row.yearValue() > java.time.Year.now().getValue() + 1) {
            warnings.add("El a\u00f1o de publicaci\u00f3n est\u00e1 en el futuro.");
        }
        if (row.entityType() == ValidationEntityType.RESEARCHER_AFFILIATION && row.endDate() != null && row.startDate() != null && row.endDate().isBefore(row.startDate())) {
            warnings.add("La fecha de fin es anterior a la fecha de inicio.");
        }
        if (row.entityType() == ValidationEntityType.EVENT_PARTICIPATION && row.startDate() == null) {
            warnings.add("La participaci\u00f3n no tiene fecha registrada.");
        }
        return warnings;
    }

    private List<String> dataQualityReminders(ResearcherActivityRow row) {
        List<String> reminders = new ArrayList<>();
        switch (row.entityType()) {
            case RESEARCHER -> {
                addIfBlank(reminders, row.email(), "Completa tu email institucional.");
                addIfBlank(reminders, row.orcid(), "A\u00f1ade tu ORCID si lo tienes.");
                addIfBlank(reminders, row.researchUnitName(), "Revisa tu afiliaci\u00f3n principal vigente.");
            }
            case RESEARCHER_AFFILIATION -> {
                addIfBlank(reminders, row.roleValue(), "Completa el rol de tu afiliaci\u00f3n.");
                if (!Boolean.TRUE.equals(row.primaryAffiliation())) {
                    reminders.add("Confirma si esta afiliaci\u00f3n debe ser principal.");
                }
            }
            case PUBLICATION -> {
                addIfBlank(reminders, row.doi(), "A\u00f1ade DOI cuando exista.");
                addIfBlank(reminders, row.sourceValue(), "Completa la fuente de la publicaci\u00f3n.");
                if (!Boolean.TRUE.equals(row.abstractPresent())) {
                    reminders.add("A\u00f1ade un resumen.");
                }
                if (row.topicCount() == null || row.topicCount() == 0) {
                    reminders.add("A\u00f1ade al menos un tema.");
                }
            }
            case EVENT_PARTICIPATION -> {
                addIfBlank(reminders, row.sourceValue(), "Vincula la participaci\u00f3n con un evento.");
                addIfBlank(reminders, row.researchUnitName(), "Indica la unidad asociada a la participaci\u00f3n.");
                if (!Boolean.TRUE.equals(row.abstractPresent())) {
                    reminders.add("A\u00f1ade una descripci\u00f3n de la participaci\u00f3n.");
                }
            }
            case RESEARCH_UNIT, SCIENTIFIC_EVENT, VENUE, PUBLISHER, TOPIC -> {
            }
        }
        return reminders;
    }

    private boolean canEdit(ValidationStatus status) {
        return status == ValidationStatus.DRAFT || status == ValidationStatus.CHANGES_REQUESTED;
    }

    private boolean canSubmit(ValidationStatus status) {
        return canEdit(status);
    }

    private boolean isAdmin(PlatformUserPrincipal user) {
        return user.roles().contains("ADMIN");
    }

    private Long requireLinkedResearcher(PlatformUserPrincipal user) {
        if (user.researcherId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current user is not linked to a researcher.");
        }
        return user.researcherId();
    }

    private String displayNameFromSubtitle(String subtitle) {
        if (subtitle == null || subtitle.isBlank()) {
            return null;
        }
        return subtitle.split("\\|", 2)[0].trim();
    }

    private void putIfPresent(Map<String, String> fields, String label, String value) {
        if (value != null && !value.isBlank()) {
            fields.put(label, value);
        }
    }

    private String booleanLabel(Boolean value) {
        if (value == null) {
            return null;
        }
        return value ? "S\u00ed" : "No";
    }

    private void addIfBlank(List<String> reminders, String value, String reminder) {
        if (value == null || value.isBlank()) {
            reminders.add(reminder);
        }
    }
}
