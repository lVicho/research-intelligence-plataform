package com.researchintelligence.platform.ai.persistence;

import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PublicationEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    public PublicationEmbeddingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("select count(*) from publication_embeddings", Long.class);
        return count == null ? 0 : count;
    }

    public boolean hasEmbeddings(String provider, String model, int dimension) {
        Long count = jdbcTemplate.queryForObject("""
            select count(*)
            from publication_embeddings
            where provider = ?
            and model = ?
            and dimension = ?
            """, Long.class, provider, model, dimension);
        return count != null && count > 0;
    }

    public boolean hasEmbeddingForPublication(Long publicationId, String provider, String model, int dimension) {
        Long count = jdbcTemplate.queryForObject("""
            select count(*)
            from publication_embeddings
            where publication_id = ?
            and provider = ?
            and model = ?
            and dimension = ?
            """, Long.class, publicationId, provider, model, dimension);
        return count != null && count > 0;
    }

    public List<Long> findPublicationIdsMissingEmbeddings(String provider, String model, int dimension) {
        return jdbcTemplate.queryForList("""
            select p.id
            from publications p
            left join publication_embeddings pe on pe.publication_id = p.id
            where pe.publication_id is null
               or pe.provider <> ?
               or pe.model <> ?
               or pe.dimension <> ?
            order by p.id asc
            """, Long.class, provider, model, dimension);
    }

    public void upsert(Long publicationId, String provider, String model, int dimension, String vector) {
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                insert into publication_embeddings (publication_id, provider, model, dimension, embedding, created_at, updated_at)
                values (?, ?, ?, ?, cast(? as vector), ?, ?)
                on conflict (publication_id) do update set
                    provider = excluded.provider,
                    model = excluded.model,
                    dimension = excluded.dimension,
                    embedding = excluded.embedding,
                    updated_at = excluded.updated_at
                """);
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            statement.setLong(1, publicationId);
            statement.setString(2, provider);
            statement.setString(3, model);
            statement.setInt(4, dimension);
            statement.setString(5, vector);
            statement.setObject(6, now);
            statement.setObject(7, now);
            return statement;
        });
    }

    public List<PublicationEmbeddingSearchRow> searchNearest(String queryVector, String provider, String model, int dimension, int limit) {
        return searchNearest(queryVector, provider, model, dimension, limit, VisibilityScope.PUBLIC_VALIDATED, null);
    }

    public List<PublicationEmbeddingSearchRow> searchNearest(
        String queryVector,
        String provider,
        String model,
        int dimension,
        int limit,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        String scope = effectiveScope(visibilityScope).name();
        return jdbcTemplate.query("""
            select pe.publication_id,
                   greatest(0, 1 - (pe.embedding <=> cast(? as vector))) as similarity_score
            from publication_embeddings pe
            join publications p on p.id = pe.publication_id
            where pe.provider = ?
            and pe.model = ?
            and pe.dimension = ?
            and (
                ? = 'ADMIN_ALL'
                or p.validation_status = 'VALIDATED'
                or (
                    ? = 'MY_DATA'
                    and cast(? as bigint) is not null
                    and exists (
                        select pa.id
                        from publication_authors pa
                        where pa.publication_id = p.id
                        and pa.researcher_id = cast(? as bigint)
                    )
                )
            )
            order by pe.embedding <=> cast(? as vector)
            limit ?
            """,
            (resultSet, rowNumber) -> new PublicationEmbeddingSearchRow(
                resultSet.getLong("publication_id"),
                resultSet.getDouble("similarity_score")
            ),
            queryVector,
            provider,
            model,
            dimension,
            scope,
            scope,
            linkedResearcherId,
            linkedResearcherId,
            queryVector,
            limit
        );
    }

    public List<PublicationEmbeddingSearchRow> searchNearestToPublication(Long publicationId, String provider, String model, int dimension, int limit) {
        return searchNearestToPublication(publicationId, provider, model, dimension, limit, VisibilityScope.PUBLIC_VALIDATED, null);
    }

    public List<PublicationEmbeddingSearchRow> searchNearestToPublication(
        Long publicationId,
        String provider,
        String model,
        int dimension,
        int limit,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        String scope = effectiveScope(visibilityScope).name();
        return jdbcTemplate.query("""
            select target.publication_id,
                   greatest(0, 1 - (target.embedding <=> source.embedding)) as similarity_score
            from publication_embeddings source
            join publications source_publication on source_publication.id = source.publication_id
            join publication_embeddings target on target.provider = source.provider
                and target.model = source.model
                and target.dimension = source.dimension
            join publications target_publication on target_publication.id = target.publication_id
            where source.publication_id = ?
            and source.provider = ?
            and source.model = ?
            and source.dimension = ?
            and target.publication_id <> source.publication_id
            and (
                ? = 'ADMIN_ALL'
                or source_publication.validation_status = 'VALIDATED'
                or (
                    ? = 'MY_DATA'
                    and cast(? as bigint) is not null
                    and exists (
                        select source_author.id
                        from publication_authors source_author
                        where source_author.publication_id = source_publication.id
                        and source_author.researcher_id = cast(? as bigint)
                    )
                )
            )
            and (
                ? = 'ADMIN_ALL'
                or target_publication.validation_status = 'VALIDATED'
                or (
                    ? = 'MY_DATA'
                    and cast(? as bigint) is not null
                    and exists (
                        select target_author.id
                        from publication_authors target_author
                        where target_author.publication_id = target_publication.id
                        and target_author.researcher_id = cast(? as bigint)
                    )
                )
            )
            order by target.embedding <=> source.embedding, target.publication_id asc
            limit ?
            """,
            (resultSet, rowNumber) -> new PublicationEmbeddingSearchRow(
                resultSet.getLong("publication_id"),
                resultSet.getDouble("similarity_score")
            ),
            publicationId,
            provider,
            model,
            dimension,
            scope,
            scope,
            linkedResearcherId,
            linkedResearcherId,
            scope,
            scope,
            linkedResearcherId,
            linkedResearcherId,
            limit
        );
    }

    private VisibilityScope effectiveScope(VisibilityScope visibilityScope) {
        return visibilityScope == null ? VisibilityScope.PUBLIC_VALIDATED : visibilityScope;
    }
}
