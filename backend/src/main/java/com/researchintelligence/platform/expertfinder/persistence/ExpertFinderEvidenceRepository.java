package com.researchintelligence.platform.expertfinder.persistence;

import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ExpertFinderEvidenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public ExpertFinderEvidenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ExpertPublicationEvidenceRow> findPublicationEvidence(
        Long researchUnitId,
        String normalizedTopic,
        String topicPattern,
        boolean onlyValidated
    ) {
        return jdbcTemplate.query("""
            select r.id as researcher_id,
                   r.full_name as researcher_full_name,
                   r.display_name as researcher_display_name,
                   r.orcid as researcher_orcid,
                   r.active as researcher_active,
                   r.validation_status as researcher_validation_status,
                   primary_ru.id as primary_research_unit_id,
                   primary_ru.name as primary_research_unit_name,
                   primary_aff.validation_status as primary_affiliation_validation_status,
                   primary_ru.validation_status as primary_research_unit_validation_status,
                   p.id as publication_id,
                   p.title as publication_title,
                   p.abstract_text as publication_abstract,
                   p.year as publication_year,
                   p.type as publication_type,
                   p.doi as publication_doi,
                   p.source as publication_source,
                   p.url as publication_url,
                   p.validation_status as publication_validation_status,
                   t.id as topic_id,
                   t.name as topic_name,
                   t.normalized_name as topic_normalized_name
            from researchers r
            join publication_authors pa on pa.researcher_id = r.id
            join publications p on p.id = pa.publication_id
            left join publication_topics pt on pt.publication_id = p.id
            left join topics t on t.id = pt.topic_id
            left join researcher_affiliations primary_aff on primary_aff.researcher_id = r.id
                and primary_aff.primary_affiliation = true
                and (primary_aff.end_date is null or primary_aff.end_date >= current_date)
            left join research_units primary_ru on primary_ru.id = primary_aff.research_unit_id
            where r.active = true
            and (? = false or (r.validation_status = 'VALIDATED' and p.validation_status = 'VALIDATED'))
            and (? = false or (
                primary_aff.validation_status = 'VALIDATED'
                and primary_ru.validation_status = 'VALIDATED'
                and primary_ru.visible_in_portal = true
                and primary_ru.organization_scope = 'INTERNAL'
                and primary_ru.active = true
            ))
            and (
                cast(? as bigint) is null
                or exists (
                    select a.id
                    from researcher_affiliations a
                    join research_units ru on ru.id = a.research_unit_id
                    where a.researcher_id = r.id
                    and a.research_unit_id = cast(? as bigint)
                    and (a.end_date is null or a.end_date >= current_date)
                    and (? = false or (a.validation_status = 'VALIDATED' and ru.validation_status = 'VALIDATED'))
                )
            )
            and (
                cast(? as varchar) is null
                or exists (
                    select pt_filter.publication_id
                    from publication_authors pa_filter
                    join publications p_filter on p_filter.id = pa_filter.publication_id
                    join publication_topics pt_filter on pt_filter.publication_id = p_filter.id
                    join topics t_filter on t_filter.id = pt_filter.topic_id
                    where pa_filter.researcher_id = r.id
                    and (? = false or p_filter.validation_status = 'VALIDATED')
                    and (
                        lower(t_filter.normalized_name) = cast(? as varchar)
                        or lower(t_filter.name) like cast(? as varchar)
                    )
                )
            )
            order by r.full_name asc, p.year desc nulls last, p.title asc, t.name asc
            """, this::mapPublicationEvidence, onlyValidated, onlyValidated, researchUnitId, researchUnitId, onlyValidated, normalizedTopic,
            onlyValidated, normalizedTopic, topicPattern);
    }

    public List<ExpertEventParticipationEvidenceRow> findEventEvidence(Long researchUnitId, boolean onlyValidated) {
        return jdbcTemplate.query("""
            select r.id as researcher_id,
                   r.full_name as researcher_full_name,
                   r.display_name as researcher_display_name,
                   r.orcid as researcher_orcid,
                   r.active as researcher_active,
                   r.validation_status as researcher_validation_status,
                   primary_ru.id as primary_research_unit_id,
                   primary_ru.name as primary_research_unit_name,
                   primary_aff.validation_status as primary_affiliation_validation_status,
                   primary_ru.validation_status as primary_research_unit_validation_status,
                   ep.id as participation_id,
                   se.id as event_id,
                   se.name as event_name,
                   se.validation_status as event_validation_status,
                   ep.research_unit_id as research_unit_id,
                   ru.name as research_unit_name,
                   ru.validation_status as research_unit_validation_status,
                   ep.participation_type_code,
                   ep.title,
                   ep.description,
                   ep.participation_date,
                   ep.related_publication_id,
                   ep.validation_status as participation_validation_status
            from event_participations ep
            join researchers r on r.id = ep.researcher_id
            join scientific_events se on se.id = ep.event_id
            left join research_units ru on ru.id = ep.research_unit_id
            left join researcher_affiliations primary_aff on primary_aff.researcher_id = r.id
                and primary_aff.primary_affiliation = true
                and (primary_aff.end_date is null or primary_aff.end_date >= current_date)
            left join research_units primary_ru on primary_ru.id = primary_aff.research_unit_id
            where r.active = true
            and (? = false or (
                r.validation_status = 'VALIDATED'
                and ep.validation_status = 'VALIDATED'
                and se.validation_status = 'VALIDATED'
                and (ep.research_unit_id is null or ru.validation_status = 'VALIDATED')
            ))
            and (? = false or (
                primary_aff.validation_status = 'VALIDATED'
                and primary_ru.validation_status = 'VALIDATED'
                and primary_ru.visible_in_portal = true
                and primary_ru.organization_scope = 'INTERNAL'
                and primary_ru.active = true
            ))
            and (
                cast(? as bigint) is null
                or ep.research_unit_id = cast(? as bigint)
                or exists (
                    select a.id
                    from researcher_affiliations a
                    join research_units aff_ru on aff_ru.id = a.research_unit_id
                    where a.researcher_id = r.id
                    and a.research_unit_id = cast(? as bigint)
                    and (a.end_date is null or a.end_date >= current_date)
                    and (? = false or (a.validation_status = 'VALIDATED' and aff_ru.validation_status = 'VALIDATED'))
                )
            )
            order by ep.participation_date desc nulls last, ep.created_at desc, ep.title asc
            """, this::mapEventEvidence, onlyValidated, onlyValidated, researchUnitId, researchUnitId, researchUnitId, onlyValidated);
    }

    private ExpertPublicationEvidenceRow mapPublicationEvidence(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ExpertPublicationEvidenceRow(
            resultSet.getLong("researcher_id"),
            resultSet.getString("researcher_full_name"),
            resultSet.getString("researcher_display_name"),
            resultSet.getString("researcher_orcid"),
            resultSet.getBoolean("researcher_active"),
            validationStatus(resultSet.getString("researcher_validation_status")),
            nullableLong(resultSet, "primary_research_unit_id"),
            resultSet.getString("primary_research_unit_name"),
            validationStatus(resultSet.getString("primary_affiliation_validation_status")),
            validationStatus(resultSet.getString("primary_research_unit_validation_status")),
            resultSet.getLong("publication_id"),
            resultSet.getString("publication_title"),
            resultSet.getString("publication_abstract"),
            nullableInteger(resultSet, "publication_year"),
            resultSet.getString("publication_type"),
            resultSet.getString("publication_doi"),
            resultSet.getString("publication_source"),
            resultSet.getString("publication_url"),
            validationStatus(resultSet.getString("publication_validation_status")),
            nullableLong(resultSet, "topic_id"),
            resultSet.getString("topic_name"),
            resultSet.getString("topic_normalized_name")
        );
    }

    private ExpertEventParticipationEvidenceRow mapEventEvidence(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ExpertEventParticipationEvidenceRow(
            resultSet.getLong("researcher_id"),
            resultSet.getString("researcher_full_name"),
            resultSet.getString("researcher_display_name"),
            resultSet.getString("researcher_orcid"),
            resultSet.getBoolean("researcher_active"),
            validationStatus(resultSet.getString("researcher_validation_status")),
            nullableLong(resultSet, "primary_research_unit_id"),
            resultSet.getString("primary_research_unit_name"),
            validationStatus(resultSet.getString("primary_affiliation_validation_status")),
            validationStatus(resultSet.getString("primary_research_unit_validation_status")),
            resultSet.getLong("participation_id"),
            resultSet.getLong("event_id"),
            resultSet.getString("event_name"),
            validationStatus(resultSet.getString("event_validation_status")),
            nullableLong(resultSet, "research_unit_id"),
            resultSet.getString("research_unit_name"),
            validationStatus(resultSet.getString("research_unit_validation_status")),
            resultSet.getString("participation_type_code"),
            resultSet.getString("title"),
            resultSet.getString("description"),
            resultSet.getObject("participation_date", java.time.LocalDate.class),
            nullableLong(resultSet, "related_publication_id"),
            validationStatus(resultSet.getString("participation_validation_status"))
        );
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private ValidationStatus validationStatus(String value) {
        return value == null ? null : ValidationStatus.valueOf(value);
    }
}
