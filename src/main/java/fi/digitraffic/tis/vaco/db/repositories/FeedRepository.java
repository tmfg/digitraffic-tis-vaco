package fi.digitraffic.tis.vaco.db.repositories;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.InvalidMappingException;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.db.model.FeedRecord;
import fi.digitraffic.tis.vaco.feeds.model.Feed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class FeedRepository {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final JdbcTemplate jdbc;

    private final ObjectMapper objectMapper;

    public FeedRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<FeedRecord> getAllFeeds() {
        try {
            return jdbc.query(
                """
                    SELECT *
                      FROM feed
                    """,
                RowMappers.FEED.apply(objectMapper));
        } catch (EmptyResultDataAccessException erdae) {
            logger.warn("Failed to find feeds", erdae);
            return List.of();
        }

    }

    @Transactional
    public Optional<FeedRecord> createFeed(Feed feed) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
            """
                INSERT INTO feed(owner_id, format, processing_enabled, uri, query_params, http_method, request_body)
                     VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
                  RETURNING id,
                            public_id,
                            owner_id,
                            format,
                            processing_enabled,
                            uri,
                            query_params,
                            http_method,
                            request_body
                """,
                RowMappers.FEED.apply(objectMapper),
                feed.owner().id(),
                feed.format().fieldName(),
                feed.processingEnabled(),
                feed.uri().uri(),
                objectMapper.writeValueAsString(feed.uri().queryParams()),
                feed.uri().httpMethod(),
                feed.uri().requestBody()
            ));
        } catch (DataAccessException dae) {
            logger.warn("Failed to create feed {}", feed, dae);
            return Optional.empty();
        } catch (JsonProcessingException e) {
            throw new InvalidMappingException("Failed to deserialize from database for feed " + feed, e);
        }

    }

    public boolean deleteByPublicId(String publicId) {
        return jdbc.update("DELETE FROM feed WHERE public_id = ?", publicId) > 0;
    }

}

