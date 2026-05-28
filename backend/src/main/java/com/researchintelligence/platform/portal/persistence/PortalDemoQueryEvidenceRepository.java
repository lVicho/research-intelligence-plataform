package com.researchintelligence.platform.portal.persistence;

import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PortalDemoQueryEvidenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public PortalDemoQueryEvidenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PortalDemoQueryEvidenceRow> findPublicationEvidence(boolean onlyValidated) {
        return jdbcTemplate.query("""
            select p.id as publication_id,
                   p.title as publication_title,
                   p.abstract_text as publication_abstract,
                   p.public_summary as publication_public_summary,
                   p.updated_at as publication_updated_at,
                   p.validation_status as publication_validation_status,
                   t.id as topic_id,
                   t.name as topic_name,
                   t.normalized_name as topic_normalized_name,
                   r.id as researcher_id,
                   r.full_name as researcher_full_name,
                   r.active as researcher_active,
                   r.validation_status as researcher_validation_status,
                   ru.id as research_unit_id,
                   ru.name as research_unit_name,
                   ru.visible_in_portal as research_unit_visible_in_portal,
                   ru.active as research_unit_active,
                   ru.organization_scope as research_unit_organization_scope,
                   ru.validation_status as research_unit_validation_status
            from publications p
            left join publication_topics pt on pt.publication_id = p.id
            left join topics t on t.id = pt.topic_id
            left join publication_authors pa on pa.publication_id = p.id and pa.researcher_id is not null
            left join researchers r on r.id = pa.researcher_id
            left join researcher_affiliations a on a.researcher_id = r.id
                and (a.end_date is null or a.end_date >= current_date)
            left join research_units ru on ru.id = a.research_unit_id
            where (? = false or p.validation_status = 'VALIDATED')
            and (? = false or r.id is null or (r.active = true and r.validation_status = 'VALIDATED'))
            and (? = false or a.id is null or a.validation_status = 'VALIDATED')
            and (? = false or ru.id is null or (ru.active = true and ru.validation_status = 'VALIDATED'))
            order by p.updated_at desc nulls last, p.id asc, t.name asc, r.full_name asc, ru.name asc
            """, this::mapRow, onlyValidated, onlyValidated, onlyValidated, onlyValidated);
    }

    private PortalDemoQueryEvidenceRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PortalDemoQueryEvidenceRow(
            resultSet.getLong("publication_id"),
            resultSet.getString("publication_title"),
            resultSet.getString("publication_abstract"),
            resultSet.getString("publication_public_summary"),
            resultSet.getObject("publication_updated_at", java.time.Instant.class),
            validationStatus(resultSet.getString("publication_validation_status")),
            nullableLong(resultSet, "topic_id"),
            resultSet.getString("topic_name"),
            resultSet.getString("topic_normalized_name"),
            nullableLong(resultSet, "researcher_id"),
            resultSet.getString("researcher_full_name"),
            resultSet.getBoolean("researcher_active"),
            validationStatus(resultSet.getString("researcher_validation_status")),
            nullableLong(resultSet, "research_unit_id"),
            resultSet.getString("research_unit_name"),
            nullableBoolean(resultSet, "research_unit_visible_in_portal"),
            nullableBoolean(resultSet, "research_unit_active"),
            resultSet.getString("research_unit_organization_scope"),
            validationStatus(resultSet.getString("research_unit_validation_status"))
        );
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Boolean nullableBoolean(ResultSet resultSet, String column) throws SQLException {
        boolean value = resultSet.getBoolean(column);
        return resultSet.wasNull() ? null : value;
    }

    private ValidationStatus validationStatus(String value) {
        return value == null ? null : ValidationStatus.valueOf(value);
    }
}
