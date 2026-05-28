package com.researchintelligence.platform.auth.persistence;

import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ResearcherWorkspaceRepository {

    private static final String OWN_ACTIVITIES_CTE = """
        with own_activities as (
            select
                'RESEARCHER'::text as entity_type,
                r.id as entity_id,
                r.full_name as title,
                nullif(concat_ws(' | ', r.display_name, r.email, r.orcid), '') as subtitle,
                r.id as researcher_id,
                r.full_name as researcher_name,
                primary_unit.research_unit_id,
                primary_unit.research_unit_name,
                r.created_at as submitted_at,
                r.validation_status,
                r.validation_comment,
                r.validated_by_user_id,
                reviewer.display_name as validated_by,
                r.validated_at,
                lower(concat_ws(' ', r.full_name, r.display_name, r.email, r.orcid, primary_unit.research_unit_name)) as search_text,
                null::text as primary_type,
                null::text as secondary_status,
                null::integer as year_value,
                null::text as doi,
                null::text as source_value,
                r.email,
                r.orcid,
                r.active,
                null::text as role_value,
                null::boolean as primary_affiliation,
                null::date as start_date,
                null::date as end_date,
                null::boolean as abstract_present,
                null::bigint as internal_author_count,
                null::bigint as topic_count
            from researchers r
            left join lateral (
                select a.research_unit_id, ru.name as research_unit_name
                from researcher_affiliations a
                join research_units ru on ru.id = a.research_unit_id
                where a.researcher_id = r.id
                and a.primary_affiliation = true
                and (a.end_date is null or a.end_date >= current_date)
                order by a.start_date desc nulls last, a.id asc
                limit 1
            ) primary_unit on true
            left join users reviewer on reviewer.id = r.validated_by_user_id
            where r.id = :researcherId

            union all

            select
                'RESEARCHER_AFFILIATION'::text as entity_type,
                a.id as entity_id,
                concat(r.full_name, ' / ', ru.name) as title,
                nullif(concat_ws(' | ', a.role, a.affiliation_type::text), '') as subtitle,
                r.id as researcher_id,
                r.full_name as researcher_name,
                ru.id as research_unit_id,
                ru.name as research_unit_name,
                a.created_at as submitted_at,
                a.validation_status,
                a.validation_comment,
                a.validated_by_user_id,
                reviewer.display_name as validated_by,
                a.validated_at,
                lower(concat_ws(' ', r.full_name, ru.name, a.role, a.affiliation_type::text)) as search_text,
                a.affiliation_type::text as primary_type,
                null::text as secondary_status,
                null::integer as year_value,
                null::text as doi,
                null::text as source_value,
                null::text as email,
                null::text as orcid,
                null::boolean as active,
                a.role as role_value,
                a.primary_affiliation,
                a.start_date,
                a.end_date,
                null::boolean as abstract_present,
                null::bigint as internal_author_count,
                null::bigint as topic_count
            from researcher_affiliations a
            join researchers r on r.id = a.researcher_id
            join research_units ru on ru.id = a.research_unit_id
            left join users reviewer on reviewer.id = a.validated_by_user_id
            where a.researcher_id = :researcherId

            union all

            select
                'PUBLICATION'::text as entity_type,
                p.id as entity_id,
                p.title,
                nullif(concat_ws(' | ', p.year::text, p.source, p.doi), '') as subtitle,
                cast(:researcherId as bigint) as researcher_id,
                author_names.researcher_name,
                null::bigint as research_unit_id,
                unit_names.research_unit_name,
                p.created_at as submitted_at,
                p.validation_status,
                p.validation_comment,
                p.validated_by_user_id,
                reviewer.display_name as validated_by,
                p.validated_at,
                lower(concat_ws(' ', p.title, p.abstract_text, p.source, p.doi, author_names.researcher_name, unit_names.research_unit_name)) as search_text,
                p.type::text as primary_type,
                p.status::text as secondary_status,
                p.year as year_value,
                p.doi,
                p.source as source_value,
                null::text as email,
                null::text as orcid,
                null::boolean as active,
                null::text as role_value,
                null::boolean as primary_affiliation,
                null::date as start_date,
                null::date as end_date,
                (p.abstract_text is not null and length(trim(p.abstract_text)) > 0) as abstract_present,
                coalesce(author_counts.internal_author_count, 0) as internal_author_count,
                coalesce(topic_counts.topic_count, 0) as topic_count
            from publications p
            left join lateral (
                select string_agg(distinct coalesce(r.display_name, r.full_name), ', ') as researcher_name
                from publication_authors pa
                join researchers r on r.id = pa.researcher_id
                where pa.publication_id = p.id
            ) author_names on true
            left join lateral (
                select string_agg(distinct ru.name, ', ') as research_unit_name
                from publication_authors pa
                join researcher_affiliations a on a.researcher_id = pa.researcher_id
                join research_units ru on ru.id = a.research_unit_id
                where pa.publication_id = p.id
                and (a.end_date is null or a.end_date >= current_date)
            ) unit_names on true
            left join lateral (
                select count(*) as internal_author_count
                from publication_authors pa
                where pa.publication_id = p.id
                and pa.researcher_id is not null
            ) author_counts on true
            left join lateral (
                select count(*) as topic_count
                from publication_topics pt
                where pt.publication_id = p.id
            ) topic_counts on true
            left join users reviewer on reviewer.id = p.validated_by_user_id
            where exists (
                select 1
                from publication_authors own_author
                where own_author.publication_id = p.id
                and own_author.researcher_id = :researcherId
            )

            union all

            select
                'EVENT_PARTICIPATION'::text as entity_type,
                ep.id as entity_id,
                ep.title,
                nullif(concat_ws(' | ', ep.participation_type_code, se.name, ep.participation_date::text), '') as subtitle,
                cast(:researcherId as bigint) as researcher_id,
                r.full_name as researcher_name,
                ru.id as research_unit_id,
                ru.name as research_unit_name,
                coalesce(ep.submitted_at, ep.created_at) as submitted_at,
                ep.validation_status,
                ep.validation_comment,
                null::bigint as validated_by_user_id,
                null::text as validated_by,
                ep.validated_at,
                lower(concat_ws(' ', ep.title, ep.description, ep.participation_type_code, se.name, se.event_type_code, r.full_name, ru.name)) as search_text,
                ep.participation_type_code as primary_type,
                se.event_type_code as secondary_status,
                extract(year from coalesce(ep.participation_date, se.start_date))::integer as year_value,
                null::text as doi,
                se.name as source_value,
                null::text as email,
                null::text as orcid,
                null::boolean as active,
                se.organizer as role_value,
                null::boolean as primary_affiliation,
                ep.participation_date as start_date,
                se.end_date,
                (ep.description is not null and length(trim(ep.description)) > 0) as abstract_present,
                null::bigint as internal_author_count,
                null::bigint as topic_count
            from event_participations ep
            join scientific_events se on se.id = ep.event_id
            join researchers r on r.id = ep.researcher_id
            left join research_units ru on ru.id = ep.research_unit_id
            where ep.researcher_id = :researcherId
        )
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ResearcherWorkspaceRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page<ResearcherActivityRow> activities(
        Long researcherId,
        ValidationStatus status,
        ValidationEntityType type,
        String text,
        int page,
        int size
    ) {
        MapSqlParameterSource parameters = parameters(researcherId, status, type, text)
            .addValue("limit", size)
            .addValue("offset", (long) page * size);
        String where = filtersSql();
        String dataSql = OWN_ACTIVITIES_CTE
            + "select * from own_activities "
            + where
            + " order by submitted_at desc nulls last, entity_type asc, entity_id asc limit :limit offset :offset";
        String countSql = OWN_ACTIVITIES_CTE + "select count(*) from own_activities " + where;
        List<ResearcherActivityRow> rows = jdbcTemplate.query(dataSql, parameters, new ResearcherActivityRowMapper());
        Long total = jdbcTemplate.queryForObject(countSql, parameters, Long.class);
        return new PageImpl<>(rows, PageRequest.of(page, size), total == null ? 0 : total);
    }

    public List<ResearcherActivityRow> recentActivities(Long researcherId, int limit) {
        MapSqlParameterSource parameters = parameters(researcherId, null, null, null)
            .addValue("limit", limit);
        String sql = OWN_ACTIVITIES_CTE
            + """
            select *
            from own_activities
            order by submitted_at desc nulls last, entity_type asc, entity_id asc
            limit :limit
            """;
        return jdbcTemplate.query(sql, parameters, new ResearcherActivityRowMapper());
    }

    public Optional<ResearcherActivityRow> findOwnedActivity(Long researcherId, ValidationEntityType type, Long entityId) {
        MapSqlParameterSource parameters = parameters(researcherId, null, type, null)
            .addValue("entityId", entityId);
        String sql = OWN_ACTIVITIES_CTE
            + """
            select *
            from own_activities
            where entity_type = :type
            and entity_id = :entityId
            """;
        return jdbcTemplate.query(sql, parameters, new ResearcherActivityRowMapper()).stream().findFirst();
    }

    public List<Object[]> countActivitiesByStatus(Long researcherId) {
        String sql = OWN_ACTIVITIES_CTE
            + """
            select validation_status, count(*)
            from own_activities
            group by validation_status
            """;
        return jdbcTemplate.query(sql, parameters(researcherId, null, null, null), (rs, rowNum) -> new Object[] {
            ValidationStatus.valueOf(rs.getString("validation_status")),
            rs.getLong("count")
        });
    }

    public List<Object[]> publicationsByYear(Long researcherId) {
        String sql = """
            select p.year, count(distinct p.id) as publication_count
            from publications p
            join publication_authors pa on pa.publication_id = p.id
            where pa.researcher_id = :researcherId
            and p.year is not null
            group by p.year
            order by p.year asc
            """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("researcherId", researcherId), (rs, rowNum) -> new Object[] {
            rs.getInt("year"),
            rs.getLong("publication_count")
        });
    }

    public List<Object[]> mainTopics(Long researcherId, int limit) {
        String sql = """
            select t.id, t.name, count(distinct p.id) as publication_count
            from topics t
            join publication_topics pt on pt.topic_id = t.id
            join publications p on p.id = pt.publication_id
            join publication_authors pa on pa.publication_id = p.id
            where pa.researcher_id = :researcherId
            group by t.id, t.name
            order by count(distinct p.id) desc, t.name asc
            limit :limit
            """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("researcherId", researcherId).addValue("limit", limit), (rs, rowNum) -> new Object[] {
            rs.getLong("id"),
            rs.getString("name"),
            rs.getLong("publication_count")
        });
    }

    private String filtersSql() {
        return """
            where (cast(:status as text) is null or validation_status = cast(:status as text))
            and (cast(:type as text) is null or entity_type = cast(:type as text))
            and (cast(:text as text) is null or search_text like cast(:text as text))
            """;
    }

    private MapSqlParameterSource parameters(Long researcherId, ValidationStatus status, ValidationEntityType type, String text) {
        String normalizedText = text == null || text.isBlank() ? null : "%" + text.toLowerCase() + "%";
        return new MapSqlParameterSource()
            .addValue("researcherId", researcherId, Types.BIGINT)
            .addValue("status", status == null ? null : status.name(), Types.VARCHAR)
            .addValue("type", type == null ? null : type.name(), Types.VARCHAR)
            .addValue("text", normalizedText, Types.VARCHAR);
    }

    private static class ResearcherActivityRowMapper implements RowMapper<ResearcherActivityRow> {
        @Override
        public ResearcherActivityRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ResearcherActivityRow(
                ValidationEntityType.valueOf(rs.getString("entity_type")),
                rs.getLong("entity_id"),
                rs.getString("title"),
                rs.getString("subtitle"),
                nullableLong(rs, "researcher_id"),
                rs.getString("researcher_name"),
                nullableLong(rs, "research_unit_id"),
                rs.getString("research_unit_name"),
                nullableInstant(rs, "submitted_at"),
                ValidationStatus.valueOf(rs.getString("validation_status")),
                rs.getString("validation_comment"),
                nullableLong(rs, "validated_by_user_id"),
                rs.getString("validated_by"),
                nullableInstant(rs, "validated_at"),
                rs.getString("primary_type"),
                rs.getString("secondary_status"),
                nullableInteger(rs, "year_value"),
                rs.getString("doi"),
                rs.getString("source_value"),
                rs.getString("email"),
                rs.getString("orcid"),
                nullableBoolean(rs, "active"),
                rs.getString("role_value"),
                nullableBoolean(rs, "primary_affiliation"),
                nullableLocalDate(rs, "start_date"),
                nullableLocalDate(rs, "end_date"),
                nullableBoolean(rs, "abstract_present"),
                nullableLong(rs, "internal_author_count"),
                nullableLong(rs, "topic_count")
            );
        }

        private static Long nullableLong(ResultSet rs, String column) throws SQLException {
            long value = rs.getLong(column);
            return rs.wasNull() ? null : value;
        }

        private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
            int value = rs.getInt(column);
            return rs.wasNull() ? null : value;
        }

        private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
            boolean value = rs.getBoolean(column);
            return rs.wasNull() ? null : value;
        }

        private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
            Timestamp timestamp = rs.getTimestamp(column);
            return timestamp == null ? null : timestamp.toInstant();
        }

        private static LocalDate nullableLocalDate(ResultSet rs, String column) throws SQLException {
            java.sql.Date date = rs.getDate(column);
            return date == null ? null : date.toLocalDate();
        }
    }
}
