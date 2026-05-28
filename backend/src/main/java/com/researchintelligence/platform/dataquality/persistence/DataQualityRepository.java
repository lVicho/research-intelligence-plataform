package com.researchintelligence.platform.dataquality.persistence;

import com.researchintelligence.platform.dataquality.domain.DataQualityEntityType;
import com.researchintelligence.platform.dataquality.domain.DataQualityIssueType;
import com.researchintelligence.platform.dataquality.domain.DataQualitySeverity;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DataQualityRepository {

    private static final String ISSUE_ITEMS_CTE = """
        with issue_items as (
            select
                'PUBLICATIONS_WITHOUT_DOI'::text as issue_type,
                'WARNING'::text as severity,
                'PUBLICATION'::text as entity_type,
                p.id as entity_id,
                p.title as title,
                'Publication has no DOI value.'::text as description,
                'Add DOI when available or document why it is unavailable.'::text as suggested_action,
                p.validation_status::text as validation_status
            from publications p
            where p.doi is null or length(trim(p.doi)) = 0

            union all

            select
                'PUBLICATIONS_WITHOUT_ABSTRACT'::text as issue_type,
                'WARNING'::text as severity,
                'PUBLICATION'::text as entity_type,
                p.id as entity_id,
                p.title as title,
                'Publication has no abstract text.'::text as description,
                'Add at least a short abstract for discoverability and review.'::text as suggested_action,
                p.validation_status::text as validation_status
            from publications p
            where p.abstract_text is null or length(trim(p.abstract_text)) = 0

            union all

            select
                'PUBLICATIONS_WITHOUT_PUBLIC_SUMMARY'::text as issue_type,
                'INFO'::text as severity,
                'PUBLICATION'::text as entity_type,
                p.id as entity_id,
                p.title as title,
                'Publication has no public summary text.'::text as description,
                'Draft a plain-language public summary from the title, abstract, and topics.'::text as suggested_action,
                p.validation_status::text as validation_status
            from publications p
            where p.public_summary is null or length(trim(p.public_summary)) = 0

            union all

            select
                'PUBLICATIONS_WITHOUT_TOPICS'::text as issue_type,
                'WARNING'::text as severity,
                'PUBLICATION'::text as entity_type,
                p.id as entity_id,
                p.title as title,
                'Publication has no linked topics.'::text as description,
                'Link one or more topics to improve thematic navigation and analytics.'::text as suggested_action,
                p.validation_status::text as validation_status
            from publications p
            where not exists (
                select 1
                from publication_topics pt
                where pt.publication_id = p.id
            )

            union all

            select
                'PUBLICATION_TITLE_CASING_ISSUES'::text as issue_type,
                'INFO'::text as severity,
                'PUBLICATION'::text as entity_type,
                p.id as entity_id,
                p.title as title,
                'Publication title appears to use all-uppercase or all-lowercase casing.'::text as description,
                'Review a normalized title-casing suggestion without changing the stored title automatically.'::text as suggested_action,
                p.validation_status::text as validation_status
            from publications p
            where p.title is not null
            and length(trim(p.title)) > 0
            and (
                p.title = upper(p.title)
                or p.title = lower(p.title)
            )

            union all

            select
                'RESEARCHERS_WITHOUT_ORCID'::text as issue_type,
                'WARNING'::text as severity,
                'RESEARCHER'::text as entity_type,
                r.id as entity_id,
                r.full_name as title,
                'Researcher has no ORCID identifier.'::text as description,
                'Add ORCID when available to reduce identity ambiguity.'::text as suggested_action,
                r.validation_status::text as validation_status
            from researchers r
            where r.orcid is null or length(trim(r.orcid)) = 0

            union all

            select
                'PUBLICATIONS_WITH_EXTERNAL_AUTHORS'::text as issue_type,
                'INFO'::text as severity,
                'PUBLICATION'::text as entity_type,
                p.id as entity_id,
                p.title as title,
                ('Publication includes ' || author_counts.external_author_count || ' external author(s).')::text as description,
                'Review external authors and enrich metadata where possible.'::text as suggested_action,
                p.validation_status::text as validation_status
            from publications p
            join lateral (
                select count(*) as external_author_count
                from publication_authors pa
                where pa.publication_id = p.id
                and pa.researcher_id is null
            ) author_counts on true
            where author_counts.external_author_count > 0

            union all

            select
                'UNRESOLVED_EXTERNAL_AUTHORS'::text as issue_type,
                'WARNING'::text as severity,
                'PUBLICATION_AUTHOR'::text as entity_type,
                pa.id as entity_id,
                coalesce(pa.external_author_name, 'Unknown external author') as title,
                ('External author in publication "' || p.title || '" has no internal match and no affiliation context.')::text as description,
                'Add affiliation details and review potential internal matches manually.'::text as suggested_action,
                p.validation_status::text as validation_status
            from publication_authors pa
            join publications p on p.id = pa.publication_id
            where pa.researcher_id is null
            and (pa.external_affiliation is null or length(trim(pa.external_affiliation)) = 0)

            union all

            select
                'ACTIVITIES_PENDING_VALIDATION'::text as issue_type,
                'WARNING'::text as severity,
                'EVENT_PARTICIPATION'::text as entity_type,
                ep.id as entity_id,
                ep.title as title,
                'Activity is pending validation.'::text as description,
                'Validate, reject, or request changes through the validation workflow.'::text as suggested_action,
                ep.validation_status::text as validation_status
            from event_participations ep
            where ep.validation_status = 'PENDING_VALIDATION'

            union all

            select
                'VENUES_WITHOUT_IDENTIFIER'::text as issue_type,
                'WARNING'::text as severity,
                'VENUE'::text as entity_type,
                v.id as entity_id,
                v.name as title,
                'Venue has no ISSN, eISSN, or ISBN identifier.'::text as description,
                'Add at least one reliable venue identifier when available.'::text as suggested_action,
                v.validation_status::text as validation_status
            from venues v
            where (v.issn is null or length(trim(v.issn)) = 0)
            and (v.eissn is null or length(trim(v.eissn)) = 0)
            and (v.isbn is null or length(trim(v.isbn)) = 0)

            union all

            select
                'EVENTS_WITHOUT_DATES'::text as issue_type,
                'WARNING'::text as severity,
                'SCIENTIFIC_EVENT'::text as entity_type,
                se.id as entity_id,
                se.name as title,
                'Scientific event has no start or end date.'::text as description,
                'Provide event dates to improve timeline analysis and filtering.'::text as suggested_action,
                se.validation_status::text as validation_status
            from scientific_events se
            where se.start_date is null
            and se.end_date is null

            union all

            select
                'EXTERNAL_ORGANIZATION_DUPLICATE_CANDIDATES'::text as issue_type,
                'WARNING'::text as severity,
                'RESEARCH_UNIT'::text as entity_type,
                ru.id as entity_id,
                ru.name as title,
                ('External organization appears in duplicate name group "' || unit_groups.unit_key || '" with ' || unit_groups.duplicate_count || ' entries.')::text as description,
                'Review external organization normalization candidates before merging or editing records.'::text as suggested_action,
                ru.validation_status::text as validation_status
            from research_units ru
            join (
                select lower(trim(name)) as unit_key, count(*) as duplicate_count
                from research_units
                where organization_scope = 'EXTERNAL'
                and name is not null
                and length(trim(name)) > 0
                group by lower(trim(name))
                having count(*) > 1
            ) unit_groups on unit_groups.unit_key = lower(trim(ru.name))
            where ru.organization_scope = 'EXTERNAL'

            union all

            select
                'DUPLICATE_TOPIC_CANDIDATES'::text as issue_type,
                'WARNING'::text as severity,
                'TOPIC'::text as entity_type,
                t.id as entity_id,
                t.name as title,
                ('Topic appears in duplicate name group "' || topic_groups.topic_key || '" with ' || topic_groups.duplicate_count || ' entries.')::text as description,
                'Review duplicate topics and merge them if they represent the same concept.'::text as suggested_action,
                null::text as validation_status
            from topics t
            join (
                select lower(trim(name)) as topic_key, count(*) as duplicate_count
                from topics
                where name is not null and length(trim(name)) > 0
                group by lower(trim(name))
                having count(*) > 1
            ) topic_groups on topic_groups.topic_key = lower(trim(t.name))

            union all

            select
                'DUPLICATE_PUBLICATION_CANDIDATES'::text as issue_type,
                'WARNING'::text as severity,
                'PUBLICATION'::text as entity_type,
                p.id as entity_id,
                p.title as title,
                ('Publication shares normalized title/year with ' || publication_groups.duplicate_count || ' entries (year=' || coalesce(p.year::text, 'null') || ').')::text as description,
                'Review potential duplicate publications and consolidate records if needed.'::text as suggested_action,
                p.validation_status::text as validation_status
            from publications p
            join (
                select lower(trim(title)) as normalized_title, coalesce(year, -1) as normalized_year, count(*) as duplicate_count
                from publications
                where title is not null and length(trim(title)) > 0
                group by lower(trim(title)), coalesce(year, -1)
                having count(*) > 1
            ) publication_groups
              on publication_groups.normalized_title = lower(trim(p.title))
             and publication_groups.normalized_year = coalesce(p.year, -1)
        )
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DataQualityRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<DataQualityIssueType, Long> countByIssueType() {
        String sql = ISSUE_ITEMS_CTE + """
            select issue_type, count(*) as issue_count
            from issue_items
            group by issue_type
            """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, new MapSqlParameterSource());
        Map<DataQualityIssueType, Long> counts = new EnumMap<>(DataQualityIssueType.class);
        for (Map<String, Object> row : rows) {
            String issueType = String.valueOf(row.get("issue_type"));
            Number count = (Number) row.get("issue_count");
            counts.put(DataQualityIssueType.valueOf(issueType), count == null ? 0L : count.longValue());
        }
        return counts;
    }

    public Page<DataQualityIssueRow> search(
        DataQualityIssueType issueType,
        DataQualitySeverity severity,
        DataQualityEntityType entityType,
        ValidationStatus validationStatus,
        int page,
        int size
    ) {
        return search(issueType, severity, entityType, null, validationStatus, page, size, null);
    }

    public Page<DataQualityIssueRow> search(
        DataQualityIssueType issueType,
        DataQualitySeverity severity,
        DataQualityEntityType entityType,
        Long entityId,
        ValidationStatus validationStatus,
        int page,
        int size,
        Long researcherId
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("issueType", issueType == null ? null : issueType.name(), Types.VARCHAR)
            .addValue("severity", severity == null ? null : severity.name(), Types.VARCHAR)
            .addValue("entityType", entityType == null ? null : entityType.name(), Types.VARCHAR)
            .addValue("entityId", entityId, Types.BIGINT)
            .addValue("validationStatus", validationStatus == null ? null : validationStatus.name(), Types.VARCHAR)
            .addValue("researcherId", researcherId, Types.BIGINT)
            .addValue("limit", size)
            .addValue("offset", (long) page * size);

        String filters = """
            where (cast(:issueType as text) is null or issue_type = cast(:issueType as text))
            and (cast(:severity as text) is null or severity = cast(:severity as text))
            and (cast(:entityType as text) is null or entity_type = cast(:entityType as text))
            and (cast(:entityId as bigint) is null or entity_id = cast(:entityId as bigint))
            and (cast(:validationStatus as text) is null or validation_status = cast(:validationStatus as text))
            and (
                cast(:researcherId as bigint) is null
                or (
                    entity_type = 'RESEARCHER'
                    and entity_id = cast(:researcherId as bigint)
                )
                or (
                    entity_type = 'PUBLICATION'
                    and exists (
                        select 1
                        from publication_authors pa_scope
                        where pa_scope.publication_id = entity_id
                        and pa_scope.researcher_id = cast(:researcherId as bigint)
                    )
                )
                or (
                    entity_type = 'PUBLICATION_AUTHOR'
                    and exists (
                        select 1
                        from publication_authors issue_author
                        join publication_authors own_author on own_author.publication_id = issue_author.publication_id
                        where issue_author.id = entity_id
                        and own_author.researcher_id = cast(:researcherId as bigint)
                    )
                )
                or (
                    entity_type = 'EVENT_PARTICIPATION'
                    and exists (
                        select 1
                        from event_participations ep_scope
                        where ep_scope.id = entity_id
                        and ep_scope.researcher_id = cast(:researcherId as bigint)
                    )
                )
            )
            """;

        String dataSql = ISSUE_ITEMS_CTE
            + "select * from issue_items "
            + filters
            + """
            order by
                case severity
                    when 'ERROR' then 1
                    when 'WARNING' then 2
                    else 3
                end asc,
                issue_type asc,
                entity_type asc,
                entity_id asc
            limit :limit offset :offset
            """;

        String countSql = ISSUE_ITEMS_CTE
            + "select count(*) from issue_items "
            + filters;

        List<DataQualityIssueRow> rows = jdbcTemplate.query(dataSql, parameters, new DataQualityIssueRowMapper());
        Long total = jdbcTemplate.queryForObject(countSql, parameters, Long.class);
        return new PageImpl<>(rows, PageRequest.of(page, size), total == null ? 0 : total);
    }

    private static class DataQualityIssueRowMapper implements RowMapper<DataQualityIssueRow> {
        @Override
        public DataQualityIssueRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            String validationStatusText = rs.getString("validation_status");
            ValidationStatus validationStatus = validationStatusText == null ? null : ValidationStatus.valueOf(validationStatusText);
            return new DataQualityIssueRow(
                DataQualityIssueType.valueOf(rs.getString("issue_type")),
                DataQualitySeverity.valueOf(rs.getString("severity")),
                DataQualityEntityType.valueOf(rs.getString("entity_type")),
                rs.getLong("entity_id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("suggested_action"),
                validationStatus
            );
        }
    }
}
