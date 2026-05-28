package com.researchintelligence.platform.audit.persistence;

import com.researchintelligence.platform.audit.domain.ActivityAuditAction;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ActivityAuditEventQueryRepository {

    private static final String BASE_SELECT = """
        select
            id,
            entity_type,
            entity_id,
            action,
            actor_user_id,
            actor_display_name,
            actor_role,
            occurred_at,
            previous_status,
            new_status,
            comment,
            changes_json
        from activity_audit_events
        """;

    private static final String BASE_FILTERS = """
        where (cast(:entityType as text) is null or entity_type = cast(:entityType as text))
        and (cast(:entityId as bigint) is null or entity_id = cast(:entityId as bigint))
        and (cast(:action as text) is null or action = cast(:action as text))
        """;

    private static final String RESEARCHER_OWNERSHIP_FILTER = """
        and (
            (entity_type = 'RESEARCHER' and entity_id = :researcherId)
            or (
                entity_type = 'RESEARCHER_AFFILIATION'
                and exists (
                    select 1
                    from researcher_affiliations a
                    where a.id = entity_id
                    and a.researcher_id = :researcherId
                )
            )
            or (
                entity_type = 'PUBLICATION'
                and exists (
                    select 1
                    from publication_authors pa
                    where pa.publication_id = entity_id
                    and pa.researcher_id = :researcherId
                )
            )
            or (
                entity_type = 'EVENT_PARTICIPATION'
                and exists (
                    select 1
                    from event_participations ep
                    where ep.id = entity_id
                    and ep.researcher_id = :researcherId
                )
            )
        )
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ActivityAuditEventQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page<ActivityAuditEventRow> findAllVisible(
        ValidationEntityType entityType,
        Long entityId,
        ActivityAuditAction action,
        int page,
        int size
    ) {
        MapSqlParameterSource parameters = parameters(entityType, entityId, action, null, page, size);
        return query(BASE_FILTERS, parameters, page, size);
    }

    public Page<ActivityAuditEventRow> findVisibleToResearcher(
        Long researcherId,
        ValidationEntityType entityType,
        Long entityId,
        ActivityAuditAction action,
        int page,
        int size
    ) {
        MapSqlParameterSource parameters = parameters(entityType, entityId, action, researcherId, page, size);
        return query(BASE_FILTERS + RESEARCHER_OWNERSHIP_FILTER, parameters, page, size);
    }

    public boolean isEntityOwnedByResearcher(ValidationEntityType entityType, Long entityId, Long researcherId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("entityType", entityType.name(), Types.VARCHAR)
            .addValue("entityId", entityId, Types.BIGINT)
            .addValue("action", null, Types.VARCHAR)
            .addValue("researcherId", researcherId, Types.BIGINT);
        String sql = "select count(*) from (" + BASE_SELECT + BASE_FILTERS + RESEARCHER_OWNERSHIP_FILTER + ") visible_events";
        Long count = jdbcTemplate.queryForObject(sql, parameters, Long.class);
        if (count != null && count > 0) {
            return true;
        }
        return switch (entityType) {
            case RESEARCHER -> entityId.equals(researcherId);
            case RESEARCHER_AFFILIATION -> exists("""
                select count(*)
                from researcher_affiliations
                where id = :entityId
                and researcher_id = :researcherId
                """, parameters);
            case PUBLICATION -> exists("""
                select count(*)
                from publication_authors
                where publication_id = :entityId
                and researcher_id = :researcherId
                """, parameters);
            case EVENT_PARTICIPATION -> exists("""
                select count(*)
                from event_participations
                where id = :entityId
                and researcher_id = :researcherId
                """, parameters);
            case RESEARCH_UNIT, SCIENTIFIC_EVENT, VENUE, PUBLISHER, TOPIC, AI_SUGGESTION -> false;
        };
    }

    private Page<ActivityAuditEventRow> query(String filters, MapSqlParameterSource parameters, int page, int size) {
        String dataSql = BASE_SELECT
            + filters
            + " order by occurred_at desc, id desc limit :limit offset :offset";
        String countSql = "select count(*) from activity_audit_events " + filters;
        List<ActivityAuditEventRow> rows = jdbcTemplate.query(dataSql, parameters, new ActivityAuditEventRowMapper());
        Long total = jdbcTemplate.queryForObject(countSql, parameters, Long.class);
        return new PageImpl<>(rows, PageRequest.of(page, size), total == null ? 0 : total);
    }

    private boolean exists(String sql, MapSqlParameterSource parameters) {
        Long count = jdbcTemplate.queryForObject(sql, parameters, Long.class);
        return count != null && count > 0;
    }

    private MapSqlParameterSource parameters(
        ValidationEntityType entityType,
        Long entityId,
        ActivityAuditAction action,
        Long researcherId,
        int page,
        int size
    ) {
        return new MapSqlParameterSource()
            .addValue("entityType", entityType == null ? null : entityType.name(), Types.VARCHAR)
            .addValue("entityId", entityId, Types.BIGINT)
            .addValue("action", action == null ? null : action.name(), Types.VARCHAR)
            .addValue("researcherId", researcherId, Types.BIGINT)
            .addValue("limit", size)
            .addValue("offset", (long) page * size);
    }

    private static class ActivityAuditEventRowMapper implements RowMapper<ActivityAuditEventRow> {
        @Override
        public ActivityAuditEventRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ActivityAuditEventRow(
                rs.getLong("id"),
                ValidationEntityType.valueOf(rs.getString("entity_type")),
                rs.getLong("entity_id"),
                ActivityAuditAction.valueOf(rs.getString("action")),
                nullableLong(rs, "actor_user_id"),
                rs.getString("actor_display_name"),
                rs.getString("actor_role"),
                nullableInstant(rs, "occurred_at"),
                nullableValidationStatus(rs, "previous_status"),
                nullableValidationStatus(rs, "new_status"),
                rs.getString("comment"),
                rs.getString("changes_json")
            );
        }

        private static Long nullableLong(ResultSet rs, String column) throws SQLException {
            long value = rs.getLong(column);
            return rs.wasNull() ? null : value;
        }

        private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
            Timestamp timestamp = rs.getTimestamp(column);
            return timestamp == null ? null : timestamp.toInstant();
        }

        private static ValidationStatus nullableValidationStatus(ResultSet rs, String column) throws SQLException {
            String value = rs.getString(column);
            return value == null ? null : ValidationStatus.valueOf(value);
        }
    }
}
