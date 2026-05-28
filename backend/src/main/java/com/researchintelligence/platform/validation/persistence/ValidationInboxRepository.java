package com.researchintelligence.platform.validation.persistence;

import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ValidationInboxRepository {

    private static final String VALIDATION_ITEMS_CTE = """
        with validation_items as (
            select
                'RESEARCH_UNIT'::text as entity_type,
                ru.id as entity_id,
                ru.name as title,
                nullif(concat_ws(' | ', ru.short_name, ru.type::text, nullif(concat_ws(', ', ru.city, ru.country), '')), '') as subtitle,
                null::bigint as researcher_id,
                null::text as researcher_name,
                ru.id as research_unit_id,
                ru.name as research_unit_name,
                coalesce(creator.display_name, 'Sistema') as submitted_by,
                ru.created_at as submitted_at,
                ru.validation_status,
                lower(concat_ws(' ', ru.name, ru.short_name, ru.type::text, ru.city, ru.country, ru.website)) as search_text,
                ru.type::text as primary_type,
                null::text as secondary_status,
                null::integer as year_value,
                null::text as doi,
                null::text as source_value,
                null::text as email,
                null::text as orcid,
                ru.active,
                ru.website,
                ru.country,
                ru.city,
                null::boolean as abstract_present,
                null::bigint as internal_author_count,
                null::bigint as topic_count,
                null::text as role_value,
                null::boolean as primary_affiliation,
                null::date as start_date,
                null::date as end_date,
                ru.validation_comment,
                ru.validated_by_user_id,
                reviewer.display_name as validated_by,
                ru.validated_at
            from research_units ru
            left join users creator on creator.id = ru.created_by_user_id
            left join users reviewer on reviewer.id = ru.validated_by_user_id

            union all

            select
                'RESEARCHER'::text as entity_type,
                r.id as entity_id,
                r.full_name as title,
                nullif(concat_ws(' | ', r.display_name, r.email, r.orcid), '') as subtitle,
                r.id as researcher_id,
                r.full_name as researcher_name,
                primary_unit.research_unit_id,
                primary_unit.research_unit_name,
                coalesce(creator.display_name, 'Sistema') as submitted_by,
                r.created_at as submitted_at,
                r.validation_status,
                lower(concat_ws(' ', r.full_name, r.display_name, r.email, r.orcid, primary_unit.research_unit_name)) as search_text,
                null::text as primary_type,
                null::text as secondary_status,
                null::integer as year_value,
                null::text as doi,
                null::text as source_value,
                r.email,
                r.orcid,
                r.active,
                null::text as website,
                null::text as country,
                null::text as city,
                null::boolean as abstract_present,
                null::bigint as internal_author_count,
                null::bigint as topic_count,
                null::text as role_value,
                null::boolean as primary_affiliation,
                null::date as start_date,
                null::date as end_date,
                r.validation_comment,
                r.validated_by_user_id,
                reviewer.display_name as validated_by,
                r.validated_at
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
            left join users creator on creator.id = r.created_by_user_id
            left join users reviewer on reviewer.id = r.validated_by_user_id

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
                coalesce(creator.display_name, 'Sistema') as submitted_by,
                a.created_at as submitted_at,
                a.validation_status,
                lower(concat_ws(' ', r.full_name, ru.name, a.role, a.affiliation_type::text)) as search_text,
                a.affiliation_type::text as primary_type,
                null::text as secondary_status,
                null::integer as year_value,
                null::text as doi,
                null::text as source_value,
                null::text as email,
                null::text as orcid,
                null::boolean as active,
                null::text as website,
                null::text as country,
                null::text as city,
                null::boolean as abstract_present,
                null::bigint as internal_author_count,
                null::bigint as topic_count,
                a.role as role_value,
                a.primary_affiliation,
                a.start_date,
                a.end_date,
                a.validation_comment,
                a.validated_by_user_id,
                reviewer.display_name as validated_by,
                a.validated_at
            from researcher_affiliations a
            join researchers r on r.id = a.researcher_id
            join research_units ru on ru.id = a.research_unit_id
            left join users creator on creator.id = a.created_by_user_id
            left join users reviewer on reviewer.id = a.validated_by_user_id

            union all

            select
                'PUBLICATION'::text as entity_type,
                p.id as entity_id,
                p.title,
                nullif(concat_ws(' | ', p.year::text, p.source, p.doi), '') as subtitle,
                null::bigint as researcher_id,
                author_names.researcher_name,
                null::bigint as research_unit_id,
                unit_names.research_unit_name,
                coalesce(creator.display_name, 'Sistema') as submitted_by,
                p.created_at as submitted_at,
                p.validation_status,
                lower(concat_ws(' ', p.title, p.abstract_text, p.source, p.doi, author_names.researcher_name, unit_names.research_unit_name)) as search_text,
                p.type::text as primary_type,
                p.status::text as secondary_status,
                p.year as year_value,
                p.doi,
                p.source as source_value,
                null::text as email,
                null::text as orcid,
                null::boolean as active,
                null::text as website,
                null::text as country,
                null::text as city,
                (p.abstract_text is not null and length(trim(p.abstract_text)) > 0) as abstract_present,
                coalesce(author_counts.internal_author_count, 0) as internal_author_count,
                coalesce(topic_counts.topic_count, 0) as topic_count,
                null::text as role_value,
                null::boolean as primary_affiliation,
                null::date as start_date,
                null::date as end_date,
                p.validation_comment,
                p.validated_by_user_id,
                reviewer.display_name as validated_by,
                p.validated_at
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
            left join users creator on creator.id = p.created_by_user_id
            left join users reviewer on reviewer.id = p.validated_by_user_id

            union all

            select
                'EVENT_PARTICIPATION'::text as entity_type,
                ep.id as entity_id,
                ep.title,
                nullif(concat_ws(' | ', ep.participation_type_code, se.name, ep.participation_date::text), '') as subtitle,
                r.id as researcher_id,
                r.full_name as researcher_name,
                ru.id as research_unit_id,
                ru.name as research_unit_name,
                coalesce(r.display_name, r.full_name, 'Sistema') as submitted_by,
                coalesce(ep.submitted_at, ep.created_at) as submitted_at,
                ep.validation_status,
                lower(concat_ws(' ', ep.title, ep.description, ep.participation_type_code, se.name, se.event_type_code, r.full_name, ru.name)) as search_text,
                ep.participation_type_code as primary_type,
                se.event_type_code as secondary_status,
                extract(year from coalesce(ep.participation_date, se.start_date))::integer as year_value,
                null::text as doi,
                se.name as source_value,
                null::text as email,
                null::text as orcid,
                null::boolean as active,
                se.website,
                se.country,
                se.city,
                (ep.description is not null and length(trim(ep.description)) > 0) as abstract_present,
                null::bigint as internal_author_count,
                null::bigint as topic_count,
                se.organizer as role_value,
                null::boolean as primary_affiliation,
                ep.participation_date as start_date,
                se.end_date,
                ep.validation_comment,
                null::bigint as validated_by_user_id,
                null::text as validated_by,
                ep.validated_at
            from event_participations ep
            join scientific_events se on se.id = ep.event_id
            join researchers r on r.id = ep.researcher_id
            left join research_units ru on ru.id = ep.research_unit_id
        )
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ValidationInboxRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page<ValidationItemRow> search(
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
        MapSqlParameterSource parameters = baseParameters(status, entityType, researcherId, researchUnitId, submittedFrom, submittedTo, text)
            .addValue("limit", size)
            .addValue("offset", (long) page * size);
        String where = filtersSql();
        String dataSql = VALIDATION_ITEMS_CTE
            + "select * from validation_items "
            + where
            + " order by " + orderBy(sort)
            + " limit :limit offset :offset";
        String countSql = VALIDATION_ITEMS_CTE
            + "select count(*) from validation_items "
            + where;

        List<ValidationItemRow> rows = jdbcTemplate.query(dataSql, parameters, new ValidationItemRowMapper());
        Long total = jdbcTemplate.queryForObject(countSql, parameters, Long.class);
        return new PageImpl<>(rows, PageRequest.of(page, size), total == null ? 0 : total);
    }

    public java.util.Optional<ValidationItemRow> findByEntity(ValidationEntityType entityType, Long entityId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("entityType", entityType.name())
            .addValue("entityId", entityId);
        String sql = VALIDATION_ITEMS_CTE
            + """
            select *
            from validation_items
            where entity_type = :entityType
            and entity_id = :entityId
            """;
        List<ValidationItemRow> rows = jdbcTemplate.query(sql, parameters, new ValidationItemRowMapper());
        return rows.stream().findFirst();
    }

    private MapSqlParameterSource baseParameters(
        ValidationStatus status,
        ValidationEntityType entityType,
        Long researcherId,
        Long researchUnitId,
        Instant submittedFrom,
        Instant submittedTo,
        String text
    ) {
        String normalizedText = text == null || text.isBlank() ? null : "%" + text.toLowerCase() + "%";
        return new MapSqlParameterSource()
            .addValue("status", status == null ? null : status.name(), Types.VARCHAR)
            .addValue("entityType", entityType == null ? null : entityType.name(), Types.VARCHAR)
            .addValue("researcherId", researcherId, Types.BIGINT)
            .addValue("researchUnitId", researchUnitId, Types.BIGINT)
            .addValue("submittedFrom", submittedFrom == null ? null : Timestamp.from(submittedFrom), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("submittedTo", submittedTo == null ? null : Timestamp.from(submittedTo), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("text", normalizedText, Types.VARCHAR);
    }

    private String filtersSql() {
        return """
            where (cast(:status as text) is null or validation_status = cast(:status as text))
            and (cast(:entityType as text) is null or entity_type = cast(:entityType as text))
            and (cast(:submittedFrom as timestamptz) is null or submitted_at >= cast(:submittedFrom as timestamptz))
            and (cast(:submittedTo as timestamptz) is null or submitted_at <= cast(:submittedTo as timestamptz))
            and (cast(:text as text) is null or search_text like cast(:text as text))
            and (cast(:researcherId as bigint) is null or (
                (entity_type = 'RESEARCHER' and entity_id = :researcherId)
                or (entity_type = 'RESEARCHER_AFFILIATION' and researcher_id = :researcherId)
                or (entity_type = 'PUBLICATION' and exists (
                    select 1
                    from publication_authors pa
                    where pa.publication_id = entity_id
                    and pa.researcher_id = :researcherId
                ))
                or (entity_type = 'EVENT_PARTICIPATION' and researcher_id = :researcherId)
            ))
            and (cast(:researchUnitId as bigint) is null or (
                (entity_type = 'RESEARCH_UNIT' and entity_id = :researchUnitId)
                or (entity_type = 'RESEARCHER_AFFILIATION' and research_unit_id = :researchUnitId)
                or (entity_type = 'EVENT_PARTICIPATION' and research_unit_id = :researchUnitId)
                or (entity_type = 'RESEARCHER' and exists (
                    select 1
                    from researcher_affiliations a
                    where a.researcher_id = entity_id
                    and a.research_unit_id = :researchUnitId
                    and (a.end_date is null or a.end_date >= current_date)
                ))
                or (entity_type = 'PUBLICATION' and exists (
                    select 1
                    from publication_authors pa
                    join researcher_affiliations a on a.researcher_id = pa.researcher_id
                    where pa.publication_id = entity_id
                    and a.research_unit_id = :researchUnitId
                    and (a.end_date is null or a.end_date >= current_date)
                ))
            ))
            """;
    }

    private String orderBy(String sort) {
        String[] parts = sort == null ? new String[0] : sort.split(",", 2);
        String sortField = parts.length == 0 || parts[0].isBlank() ? "submittedAt" : parts[0].trim();
        String direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim()) ? "asc" : "desc";
        String column = switch (sortField) {
            case "title" -> "title";
            case "entityType" -> "entity_type";
            case "researcherName" -> "researcher_name";
            case "researchUnitName" -> "research_unit_name";
            case "validationStatus" -> "validation_status";
            default -> "submitted_at";
        };
        return column + " " + direction + " nulls last, entity_type asc, entity_id asc";
    }

    private static class ValidationItemRowMapper implements RowMapper<ValidationItemRow> {
        @Override
        public ValidationItemRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ValidationItemRow(
                ValidationEntityType.valueOf(rs.getString("entity_type")),
                rs.getLong("entity_id"),
                rs.getString("title"),
                rs.getString("subtitle"),
                nullableLong(rs, "researcher_id"),
                rs.getString("researcher_name"),
                nullableLong(rs, "research_unit_id"),
                rs.getString("research_unit_name"),
                rs.getString("submitted_by"),
                nullableInstant(rs, "submitted_at"),
                ValidationStatus.valueOf(rs.getString("validation_status")),
                rs.getString("primary_type"),
                rs.getString("secondary_status"),
                nullableInteger(rs, "year_value"),
                rs.getString("doi"),
                rs.getString("source_value"),
                rs.getString("email"),
                rs.getString("orcid"),
                nullableBoolean(rs, "active"),
                rs.getString("website"),
                rs.getString("country"),
                rs.getString("city"),
                nullableBoolean(rs, "abstract_present"),
                nullableLong(rs, "internal_author_count"),
                nullableLong(rs, "topic_count"),
                rs.getString("role_value"),
                nullableBoolean(rs, "primary_affiliation"),
                nullableLocalDate(rs, "start_date"),
                nullableLocalDate(rs, "end_date"),
                rs.getString("validation_comment"),
                nullableLong(rs, "validated_by_user_id"),
                rs.getString("validated_by"),
                nullableInstant(rs, "validated_at")
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
