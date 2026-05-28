package com.researchintelligence.platform.ingestion.application;

import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.audit.application.AuditFieldChange;
import com.researchintelligence.platform.ingestion.api.PublicationCsvIngestionReportResponse;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorEntity;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationTopicEntity;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.TopicEntity;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicationCsvIngestionService {

    private final PublicationRepository publicationRepository;
    private final PublicationAuthorRepository authorRepository;
    private final TopicRepository topicRepository;
    private final PublicationTopicRepository publicationTopicRepository;
    private final PublicationIngestionMatcher matcher;
    private final ActivityAuditService auditService;

    public PublicationCsvIngestionService(
        PublicationRepository publicationRepository,
        PublicationAuthorRepository authorRepository,
        TopicRepository topicRepository,
        PublicationTopicRepository publicationTopicRepository,
        PublicationIngestionMatcher matcher,
        ActivityAuditService auditService
    ) {
        this.publicationRepository = publicationRepository;
        this.authorRepository = authorRepository;
        this.topicRepository = topicRepository;
        this.publicationTopicRepository = publicationTopicRepository;
        this.matcher = matcher;
        this.auditService = auditService;
    }

    @Transactional
    public PublicationCsvIngestionReportResponse ingest(InputStream inputStream) throws IOException {
        List<CsvRecord> records = PublicationCsvParser.parse(inputStream);
        PublicationCsvIngestionReport report = new PublicationCsvIngestionReport();
        report.totalRows(records.size());
        for (CsvRecord record : records) {
            ingestRecord(record, report);
        }
        return report.toResponse();
    }

    private void ingestRecord(CsvRecord record, PublicationCsvIngestionReport report) {
        try {
            String title = IngestionNormalizer.displayText(record.get("title"));
            if (title.isBlank()) {
                skip(record, report, "Title is required.");
                return;
            }
            Integer year = parseYear(record.get("year"));
            PublicationType type = parseEnum(record.get("type"), PublicationType.class, PublicationType.OTHER);
            PublicationStatus status = parseEnum(record.get("status"), PublicationStatus.class, PublicationStatus.UNKNOWN);
            List<String> authorNames = splitMultiValue(record.get("authors"))
                .stream()
                .filter(authorName -> !authorName.isBlank())
                .toList();
            if (authorNames.isEmpty()) {
                skip(record, report, "At least one author is required.");
                return;
            }

            String doi = IngestionNormalizer.blankToNull(IngestionNormalizer.normalizeDoi(record.get("doi")));
            Optional<PublicationEntity> existing = matcher.findPublication(doi, title, year);
            PublicationEntity publication = existing.orElseGet(() -> new PublicationEntity(
                title,
                IngestionNormalizer.blankToNull(record.get("abstracttext")),
                year,
                type,
                status,
                doi,
                IngestionNormalizer.blankToNull(record.get("source")),
                IngestionNormalizer.blankToNull(record.get("url"))
            ));
            ValidationStatus previousStatus = publication.getValidationStatus();
            Map<String, AuditFieldChange> changes = publicationChanges(publication, record, title, year, type, status, doi);
            updatePublication(publication, record, title, year, type, status, doi);
            publication = publicationRepository.save(publication);
            if (existing.isPresent()) {
                report.updatedPublication();
                auditService.recordUpdated(ValidationEntityType.PUBLICATION, publication.getId(), previousStatus, publication.getValidationStatus(), changes);
            } else {
                report.insertedPublication();
                auditService.recordCreated(ValidationEntityType.PUBLICATION, publication.getId(), publication.getValidationStatus());
            }

            replaceAuthors(publication.getId(), record, authorNames, report);
            replaceTopics(publication.getId(), splitMultiValue(record.get("topics")), report);
        } catch (RuntimeException exception) {
            skip(record, report, exception.getMessage());
        }
    }

    private void updatePublication(
        PublicationEntity publication,
        CsvRecord record,
        String title,
        Integer year,
        PublicationType type,
        PublicationStatus status,
        String doi
    ) {
        publication.setTitle(title);
        publication.setAbstractText(IngestionNormalizer.blankToNull(record.get("abstracttext")));
        publication.setPublicationYear(year);
        publication.setType(type);
        publication.setStatus(status);
        publication.setDoi(doi);
        publication.setSource(IngestionNormalizer.blankToNull(record.get("source")));
        publication.setUrl(IngestionNormalizer.blankToNull(record.get("url")));
    }

    private Map<String, AuditFieldChange> publicationChanges(
        PublicationEntity publication,
        CsvRecord record,
        String title,
        Integer year,
        PublicationType type,
        PublicationStatus status,
        String doi
    ) {
        Map<String, AuditFieldChange> changes = auditService.changes();
        auditService.addChange(changes, "title", publication.getTitle(), title);
        auditService.addChange(changes, "abstractText", publication.getAbstractText(), IngestionNormalizer.blankToNull(record.get("abstracttext")));
        auditService.addChange(changes, "publicationYear", publication.getPublicationYear(), year);
        auditService.addChange(changes, "type", publication.getType(), type);
        auditService.addChange(changes, "status", publication.getStatus(), status);
        auditService.addChange(changes, "doi", publication.getDoi(), doi);
        auditService.addChange(changes, "source", publication.getSource(), IngestionNormalizer.blankToNull(record.get("source")));
        auditService.addChange(changes, "url", publication.getUrl(), IngestionNormalizer.blankToNull(record.get("url")));
        return changes;
    }

    private void replaceAuthors(Long publicationId, CsvRecord record, List<String> authorNames, PublicationCsvIngestionReport report) {
        List<String> orcids = splitMultiValue(record.get("internalauthororcids"));
        List<String> internalNames = splitMultiValue(record.get("internalauthornames"));
        List<String> externalAffiliations = splitMultiValue(record.get("externalaffiliations"));
        Set<String> internalNameSet = new LinkedHashSet<>(internalNames.stream().map(IngestionNormalizer::normalizeText).toList());
        List<PublicationAuthorDraft> drafts = new ArrayList<>();
        Set<String> identities = new LinkedHashSet<>();

        for (int i = 0; i < authorNames.size(); i++) {
            String authorName = IngestionNormalizer.displayText(authorNames.get(i));
            String orcid = valueAt(orcids, i);
            String internalName = valueAt(internalNames, i);
            String nameForMatching = !internalName.isBlank() ? internalName : authorName;
            Optional<ResearcherEntity> researcher = matcher.findInternalAuthor(orcid, nameForMatching);
            String externalAffiliation = IngestionNormalizer.blankToNull(valueAt(externalAffiliations, i));

            PublicationAuthorDraft draft;
            if (researcher.isPresent()) {
                report.matchedInternalAuthor();
                draft = new PublicationAuthorDraft(researcher.get().getId(), null, null, i + 1);
            } else {
                if (!orcid.isBlank() || !internalName.isBlank() || internalNameSet.contains(IngestionNormalizer.normalizeText(authorName))) {
                    report.rowError(record.rowNumber(), "Internal author was not matched and was stored as external: " + authorName);
                }
                draft = new PublicationAuthorDraft(null, authorName, externalAffiliation, i + 1);
                report.externalAuthorStored();
            }

            if (identities.add(draft.identityKey())) {
                drafts.add(draft);
            } else {
                report.rowError(record.rowNumber(), "Duplicate author relationship skipped: " + authorName);
            }
        }

        authorRepository.deleteByPublicationId(publicationId);
        authorRepository.saveAll(drafts.stream()
            .sorted(Comparator.comparing(PublicationAuthorDraft::authorOrder))
            .map(draft -> new PublicationAuthorEntity(
                publicationId,
                draft.researcherId(),
                draft.externalAuthorName(),
                draft.externalAffiliation(),
                draft.authorOrder(),
                false
            ))
            .toList());
    }

    private void replaceTopics(Long publicationId, List<String> topicNames, PublicationCsvIngestionReport report) {
        Map<String, String> displayNamesByNormalized = new LinkedHashMap<>();
        for (String topicName : topicNames) {
            String normalized = IngestionNormalizer.normalizeText(topicName);
            if (!normalized.isBlank()) {
                displayNamesByNormalized.putIfAbsent(normalized, IngestionNormalizer.displayText(topicName));
            }
        }

        List<TopicEntity> topics = new ArrayList<>();
        for (Map.Entry<String, String> entry : displayNamesByNormalized.entrySet()) {
            TopicEntity topic = topicRepository.findByNormalizedName(entry.getKey())
                .orElseGet(() -> {
                    report.createdTopic();
                    return topicRepository.save(new TopicEntity(entry.getValue(), entry.getKey()));
                });
            topics.add(topic);
        }

        publicationTopicRepository.deleteByPublicationId(publicationId);
        publicationTopicRepository.saveAll(topics.stream()
            .map(topic -> new PublicationTopicEntity(publicationId, topic.getId()))
            .toList());
    }

    private void skip(CsvRecord record, PublicationCsvIngestionReport report, String message) {
        report.skippedRow();
        report.rowError(record.rowNumber(), message == null || message.isBlank() ? "Row could not be imported." : message);
    }

    private Integer parseYear(String value) {
        String text = IngestionNormalizer.displayText(value);
        if (text.isBlank()) {
            return null;
        }
        int year = Integer.parseInt(text);
        if (year < 1500 || year > 2200) {
            throw new IllegalArgumentException("Publication year must be between 1500 and 2200.");
        }
        return year;
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumType, T defaultValue) {
        String text = IngestionNormalizer.displayText(value);
        if (text.isBlank()) {
            return defaultValue;
        }
        return Enum.valueOf(enumType, text.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
    }

    private List<String> splitMultiValue(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank()) {
            return List.of();
        }
        String delimiter = text.contains(";") ? ";" : "\\|";
        return List.of(text.split(delimiter, -1)).stream()
            .map(IngestionNormalizer::displayText)
            .toList();
    }

    private String valueAt(List<String> values, int index) {
        return index < values.size() ? IngestionNormalizer.displayText(values.get(index)) : "";
    }
}
