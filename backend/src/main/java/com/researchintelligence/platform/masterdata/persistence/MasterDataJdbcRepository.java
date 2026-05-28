package com.researchintelligence.platform.masterdata.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MasterDataJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public MasterDataJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MasterDataRow> findActive(MasterDataCategory category) {
        String sql = """
            select id, code, label_es, description_es, active, sort_order, created_at, updated_at
            from %s
            where active = true
            order by sort_order asc, label_es asc, code asc
            """.formatted(category.tableName());
        return jdbcTemplate.query(sql, this::mapRow);
    }

    private MasterDataRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new MasterDataRow(
            resultSet.getLong("id"),
            resultSet.getString("code"),
            resultSet.getString("label_es"),
            resultSet.getString("description_es"),
            resultSet.getBoolean("active"),
            resultSet.getInt("sort_order"),
            instant(resultSet, "created_at"),
            instant(resultSet, "updated_at")
        );
    }

    private Instant instant(ResultSet resultSet, String columnName) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
