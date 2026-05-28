package com.researchintelligence.platform.validation.application;

import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.events.persistence.EventParticipationEntity;
import com.researchintelligence.platform.events.persistence.EventParticipationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.validation.api.ValidationItemDetailResponse;
import com.researchintelligence.platform.validation.api.ValidationItemResponse;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import com.researchintelligence.platform.validation.persistence.ValidationInboxRepository;
import com.researchintelligence.platform.validation.persistence.ValidationItemRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ValidationInboxService {

    private final ValidationInboxRepository inboxRepository;
    private final ResearchUnitRepository researchUnitRepository;
    private final ResearcherRepository researcherRepository;
    private final ResearcherAffiliationRepository affiliationRepository;
    private final PublicationRepository publicationRepository;
    private final EventParticipationRepository eventParticipationRepository;
    private final ActivityAuditService auditService;

    public ValidationInboxService(
        ValidationInboxRepository inboxRepository,
        ResearchUnitRepository researchUnitRepository,
        ResearcherRepository researcherRepository,
        ResearcherAffiliationRepository affiliationRepository,
        PublicationRepository publicationRepository,
        EventParticipationRepository eventParticipationRepository,
        ActivityAuditService auditService
    ) {
        this.inboxRepository = inboxRepository;
        this.researchUnitRepository = researchUnitRepository;
        this.researcherRepository = researcherRepository;
        this.affiliationRepository = affiliationRepository;
        this.publicationRepository = publicationRepository;
        this.eventParticipationRepository = eventParticipationRepository;
        this.auditService = auditService;
    }

    public PageResponse<ValidationItemResponse> search(
        ValidationStatus status,
        ValidationEntityType entityType,
        Long researcherId,
        Long researchUnitId,
        Instant submittedFrom,
        Instant submittedTo,
        String text,
        int page,
        int size,
        String sort
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        ValidationStatus effectiveStatus = status == null ? ValidationStatus.PENDING_VALIDATION : status;
        Page<ValidationItemRow> rows = inboxRepository.search(
            effectiveStatus,
            entityType,
            researcherId,
            researchUnitId,
            submittedFrom,
            submittedTo,
            text,
            safePage,
            safeSize,
            sort
        );
        return PageResponse.from(rows.map(this::toResponse));
    }

    public ValidationItemDetailResponse findDetail(ValidationEntityType entityType, Long entityId) {
        ValidationItemRow row = findRow(entityType, entityId);
        return new ValidationItemDetailResponse(
            toResponse(row),
            detailFields(row),
            row.validationComment(),
            row.validatedBy(),
            row.validatedAt(),
            warnings(row),
            dataQualityFlags(row)
        );
    }

    @Transactional
    public ValidationItemDetailResponse validate(ValidationEntityType entityType, Long entityId, String comment, Long userId) {
        applyStatus(entityType, entityId, ValidationStatus.VALIDATED, comment, userId);
        return findDetail(entityType, entityId);
    }

    @Transactional
    public ValidationItemDetailResponse reject(ValidationEntityType entityType, Long entityId, String comment, Long userId) {
        applyStatus(entityType, entityId, ValidationStatus.REJECTED, comment, userId);
        return findDetail(entityType, entityId);
    }

    @Transactional
    public ValidationItemDetailResponse requestChanges(ValidationEntityType entityType, Long entityId, String comment, Long userId) {
        applyStatus(entityType, entityId, ValidationStatus.CHANGES_REQUESTED, comment, userId);
        return findDetail(entityType, entityId);
    }

    private void applyStatus(ValidationEntityType entityType, Long entityId, ValidationStatus status, String comment, Long userId) {
        Instant now = Instant.now();
        String normalizedComment = comment == null || comment.isBlank() ? null : comment.trim();
        switch (entityType) {
            case RESEARCH_UNIT -> {
                ResearchUnitEntity entity = researchUnitRepository.findById(entityId)
                    .orElseThrow(() -> new ResourceNotFoundException("ResearchUnit", entityId));
                ValidationStatus previousStatus = entity.getValidationStatus();
                validateTransition(previousStatus, status, normalizedComment);
                entity.setValidationStatus(status);
                entity.setValidationComment(normalizedComment);
                entity.setValidatedByUserId(userId);
                entity.setValidatedAt(now);
                auditService.recordStatusChange(entityType, entityId, previousStatus, status, normalizedComment);
            }
            case RESEARCHER -> {
                ResearcherEntity entity = researcherRepository.findById(entityId)
                    .orElseThrow(() -> new ResourceNotFoundException("Researcher", entityId));
                ValidationStatus previousStatus = entity.getValidationStatus();
                validateTransition(previousStatus, status, normalizedComment);
                entity.setValidationStatus(status);
                entity.setValidationComment(normalizedComment);
                entity.setValidatedByUserId(userId);
                entity.setValidatedAt(now);
                auditService.recordStatusChange(entityType, entityId, previousStatus, status, normalizedComment);
            }
            case RESEARCHER_AFFILIATION -> {
                ResearcherAffiliationEntity entity = affiliationRepository.findById(entityId)
                    .orElseThrow(() -> new ResourceNotFoundException("ResearcherAffiliation", entityId));
                ValidationStatus previousStatus = entity.getValidationStatus();
                validateTransition(previousStatus, status, normalizedComment);
                entity.setValidationStatus(status);
                entity.setValidationComment(normalizedComment);
                entity.setValidatedByUserId(userId);
                entity.setValidatedAt(now);
                auditService.recordStatusChange(entityType, entityId, previousStatus, status, normalizedComment);
            }
            case PUBLICATION -> {
                PublicationEntity entity = publicationRepository.findById(entityId)
                    .orElseThrow(() -> new ResourceNotFoundException("Publication", entityId));
                ValidationStatus previousStatus = entity.getValidationStatus();
                validateTransition(previousStatus, status, normalizedComment);
                entity.setValidationStatus(status);
                entity.setValidationComment(normalizedComment);
                entity.setValidatedByUserId(userId);
                entity.setValidatedAt(now);
                auditService.recordStatusChange(entityType, entityId, previousStatus, status, normalizedComment);
            }
            case EVENT_PARTICIPATION -> {
                EventParticipationEntity entity = eventParticipationRepository.findById(entityId)
                    .orElseThrow(() -> new ResourceNotFoundException("EventParticipation", entityId));
                ValidationStatus previousStatus = entity.getValidationStatus();
                validateTransition(previousStatus, status, normalizedComment);
                entity.setValidationStatus(status);
                entity.setValidationComment(normalizedComment);
                entity.setValidatedAt(now);
                auditService.recordStatusChange(entityType, entityId, previousStatus, status, normalizedComment);
            }
            case SCIENTIFIC_EVENT, VENUE, PUBLISHER, TOPIC ->
                throw new BusinessRuleException("This entity type is not a validation inbox item.");
        }
    }

    private void validateTransition(ValidationStatus previousStatus, ValidationStatus nextStatus, String comment) {
        if (previousStatus != ValidationStatus.PENDING_VALIDATION) {
            throw new BusinessRuleException("Only pending validation items can be validated, rejected, or returned for changes.");
        }
        if (nextStatus != ValidationStatus.VALIDATED
            && nextStatus != ValidationStatus.REJECTED
            && nextStatus != ValidationStatus.CHANGES_REQUESTED) {
            throw new BusinessRuleException("Unsupported validation status transition.");
        }
        if ((nextStatus == ValidationStatus.REJECTED || nextStatus == ValidationStatus.CHANGES_REQUESTED)
            && (comment == null || comment.isBlank())) {
            throw new BusinessRuleException("A validation comment is required when rejecting an item or requesting changes.");
        }
    }

    private ValidationItemRow findRow(ValidationEntityType entityType, Long entityId) {
        return inboxRepository.findByEntity(entityType, entityId)
            .orElseThrow(() -> new ResourceNotFoundException(entityType.name(), entityId));
    }

    private ValidationItemResponse toResponse(ValidationItemRow row) {
        return new ValidationItemResponse(
            row.entityType(),
            row.entityId(),
            row.title(),
            row.subtitle(),
            row.researcherName(),
            row.researchUnitName(),
            row.submittedBy(),
            row.submittedAt(),
            row.validationStatus(),
            summaryFields(row),
            warnings(row),
            dataQualityFlags(row)
        );
    }

    private Map<String, String> summaryFields(ValidationItemRow row) {
        Map<String, String> fields = new LinkedHashMap<>();
        switch (row.entityType()) {
            case RESEARCH_UNIT -> {
                putIfPresent(fields, "Tipo", row.primaryType());
                putIfPresent(fields, "Ubicaci\u00f3n", join(row.city(), row.country()));
                putIfPresent(fields, "Activa", booleanLabel(row.active()));
            }
            case RESEARCHER -> {
                putIfPresent(fields, "Email", row.email());
                putIfPresent(fields, "ORCID", row.orcid());
                putIfPresent(fields, "Activo", booleanLabel(row.active()));
            }
            case RESEARCHER_AFFILIATION -> {
                putIfPresent(fields, "Tipo de afiliaci\u00f3n", row.primaryType());
                putIfPresent(fields, "Rol", row.roleValue());
                putIfPresent(fields, "Principal", booleanLabel(row.primaryAffiliation()));
            }
            case PUBLICATION -> {
                putIfPresent(fields, "Tipo", row.primaryType());
                putIfPresent(fields, "Estado", row.secondaryStatus());
                putIfPresent(fields, "A\u00f1o", row.yearValue() == null ? null : row.yearValue().toString());
                putIfPresent(fields, "Fuente", row.sourceValue());
            }
            case EVENT_PARTICIPATION -> {
                putIfPresent(fields, "Tipo de participaci\u00f3n", row.primaryType());
                putIfPresent(fields, "Evento", row.sourceValue());
                putIfPresent(fields, "Investigador", row.researcherName());
                putIfPresent(fields, "Unidad", row.researchUnitName());
            }
            case SCIENTIFIC_EVENT, VENUE, PUBLISHER, TOPIC -> putIfPresent(fields, "Tema", row.title());
        }
        return fields;
    }

    private Map<String, String> detailFields(ValidationItemRow row) {
        Map<String, String> fields = new LinkedHashMap<>(summaryFields(row));
        switch (row.entityType()) {
            case RESEARCH_UNIT -> {
                putIfPresent(fields, "Nombre", row.title());
                putIfPresent(fields, "Sitio web", row.website());
            }
            case RESEARCHER -> {
                putIfPresent(fields, "Nombre completo", row.title());
                putIfPresent(fields, "Nombre p\u00fablico", row.subtitle());
                putIfPresent(fields, "Afiliaci\u00f3n principal", row.researchUnitName());
            }
            case RESEARCHER_AFFILIATION -> {
                putIfPresent(fields, "Investigador", row.researcherName());
                putIfPresent(fields, "Unidad", row.researchUnitName());
                putIfPresent(fields, "Fecha de inicio", row.startDate() == null ? null : row.startDate().toString());
                putIfPresent(fields, "Fecha de fin", row.endDate() == null ? null : row.endDate().toString());
            }
            case PUBLICATION -> {
                putIfPresent(fields, "T\u00edtulo", row.title());
                putIfPresent(fields, "DOI", row.doi());
                putIfPresent(fields, "Investigadores internos", row.internalAuthorCount() == null ? null : row.internalAuthorCount().toString());
                putIfPresent(fields, "Temas", row.topicCount() == null ? null : row.topicCount().toString());
            }
            case EVENT_PARTICIPATION -> {
                putIfPresent(fields, "T\u00edtulo", row.title());
                putIfPresent(fields, "Evento", row.sourceValue());
                putIfPresent(fields, "Tipo de evento", row.secondaryStatus());
                putIfPresent(fields, "Fecha de participaci\u00f3n", row.startDate() == null ? null : row.startDate().toString());
                putIfPresent(fields, "Ubicaci\u00f3n", join(row.city(), row.country()));
            }
            case SCIENTIFIC_EVENT, VENUE, PUBLISHER, TOPIC -> putIfPresent(fields, "Nombre", row.title());
        }
        return fields;
    }

    private List<String> warnings(ValidationItemRow row) {
        List<String> warnings = new ArrayList<>();
        if (row.entityType() == ValidationEntityType.RESEARCH_UNIT && Boolean.FALSE.equals(row.active())) {
            warnings.add("La unidad est\u00e1 marcada como inactiva.");
        }
        if (row.entityType() == ValidationEntityType.RESEARCHER && Boolean.FALSE.equals(row.active())) {
            warnings.add("El investigador est\u00e1 marcado como inactivo.");
        }
        if (row.entityType() == ValidationEntityType.PUBLICATION && row.yearValue() != null && row.yearValue() > java.time.Year.now().getValue() + 1) {
            warnings.add("El a\u00f1o de publicaci\u00f3n est\u00e1 en el futuro.");
        }
        if (row.entityType() == ValidationEntityType.RESEARCHER_AFFILIATION && row.endDate() != null && row.startDate() != null && row.endDate().isBefore(row.startDate())) {
            warnings.add("La fecha de fin es anterior a la fecha de inicio.");
        }
        if (row.entityType() == ValidationEntityType.EVENT_PARTICIPATION && row.startDate() != null && row.yearValue() != null && row.yearValue() > java.time.Year.now().getValue() + 2) {
            warnings.add("La fecha de participaci\u00f3n est\u00e1 en el futuro.");
        }
        return warnings;
    }

    private List<String> dataQualityFlags(ValidationItemRow row) {
        List<String> flags = new ArrayList<>();
        switch (row.entityType()) {
            case RESEARCH_UNIT -> {
                addIfBlank(flags, row.country(), "Sin pa\u00eds");
                addIfBlank(flags, row.city(), "Sin ciudad");
                addIfBlank(flags, row.website(), "Sin sitio web");
            }
            case RESEARCHER -> {
                addIfBlank(flags, row.email(), "Sin email");
                addIfBlank(flags, row.orcid(), "Sin ORCID");
                addIfBlank(flags, row.researchUnitName(), "Sin afiliaci\u00f3n principal vigente");
            }
            case RESEARCHER_AFFILIATION -> {
                addIfBlank(flags, row.roleValue(), "Sin rol");
                if (!Boolean.TRUE.equals(row.primaryAffiliation())) {
                    flags.add("No es afiliaci\u00f3n principal");
                }
            }
            case PUBLICATION -> {
                addIfBlank(flags, row.doi(), "Sin DOI");
                addIfBlank(flags, row.sourceValue(), "Sin fuente");
                if (!Boolean.TRUE.equals(row.abstractPresent())) {
                    flags.add("Sin resumen");
                }
                if (row.internalAuthorCount() == null || row.internalAuthorCount() == 0) {
                    flags.add("Sin autores internos");
                }
                if (row.topicCount() == null || row.topicCount() == 0) {
                    flags.add("Sin temas");
                }
            }
            case EVENT_PARTICIPATION -> {
                addIfBlank(flags, row.sourceValue(), "Sin evento");
                addIfBlank(flags, row.researcherName(), "Sin investigador");
                addIfBlank(flags, row.researchUnitName(), "Sin unidad");
                if (!Boolean.TRUE.equals(row.abstractPresent())) {
                    flags.add("Sin descripci\u00f3n");
                }
            }
            case SCIENTIFIC_EVENT, VENUE, PUBLISHER, TOPIC -> {
            }
        }
        return flags;
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

    private String join(String first, String second) {
        if ((first == null || first.isBlank()) && (second == null || second.isBlank())) {
            return null;
        }
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + ", " + second;
    }

    private void addIfBlank(List<String> flags, String value, String flag) {
        if (value == null || value.isBlank()) {
            flags.add(flag);
        }
    }
}
