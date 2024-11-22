package fi.digitraffic.tis.vaco.credentials;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.InvalidMappingException;
import fi.digitraffic.tis.vaco.credentials.model.AuthenticationDetails;
import fi.digitraffic.tis.vaco.credentials.model.Credentials;
import fi.digitraffic.tis.vaco.credentials.model.CredentialsType;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.CredentialsRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class CredentialsRepository {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JdbcTemplate jdbc;

    private final ObjectMapper objectMapper;

    public CredentialsRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public Optional<CredentialsRecord> createCredentials(Credentials credentials, CompanyRecord owner) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                """
                INSERT INTO credentials(owner_id, name, description, type, details)
                     VALUES (?, ?, ?, ?::credentials_type, ?)
                  RETURNING id,
                            public_id,
                            owner_id,
                            name,
                            type,
                            description,
                            details
                """,
                RowMappers.CREDENTIALS_RECORD(this::decryptBlob),
                owner.id(),
                credentials.name(),
                credentials.description(),
                credentials.type().fieldName(),
                encryptBlob(credentials.details())
            ));
        } catch (DataAccessException dae) {
            logger.warn("Failed to create credentials", dae);
            return Optional.empty();
        }
    }

    public List<CredentialsRecord> findAllForCompany(CompanyRecord companyRecord) {
        try {
            return jdbc.query(
                """
                SELECT *
                  FROM credentials
                 WHERE owner_id = ?
                """,
                RowMappers.CREDENTIALS_RECORD(this::decryptBlob),
                companyRecord.id());
        } catch (EmptyResultDataAccessException erdae) {
            logger.warn("Failed to find all by business id", erdae);
            return List.of();
        }
    }

    public Optional<CredentialsRecord> findByPublicId(String publicId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                """
                SELECT *
                  FROM credentials c
                 WHERE c.public_id = ?
                """,
                RowMappers.CREDENTIALS_RECORD(this::decryptBlob),
                publicId));
        } catch (EmptyResultDataAccessException erdae) {
            logger.warn("No credentials available by publicId {}", publicId, erdae);
            return Optional.empty();
        }
    }
    public CredentialsRecord updateCredentials(CredentialsRecord previous, CredentialsType type, String name, String description, AuthenticationDetails details) {
        return jdbc.queryForObject(
            """
               UPDATE credentials
                  SET name = ?,
                      description = ?,
                      type = ?::credentials_type,
                      details = ?
                WHERE id = ?
            RETURNING *
            """,
            RowMappers.CREDENTIALS_RECORD(this::decryptBlob),
            name,
            description,
            type.fieldName(),
            encryptBlob(details),
            previous.id()
        );
    }

    public Optional<Boolean> deleteCredentials(String publicId) {
        try {
            return Optional.of(jdbc.update("DELETE FROM credentials WHERE public_id = ?", publicId) > 0);
        } catch (DataAccessException dae) {
            logger.warn("Someone tried to delete non-existent credentials by publicId {}", publicId, dae);
            return Optional.empty();
        }
    }

    private byte[] encryptBlob(AuthenticationDetails details) {
        // TODO: temporary solution until EncryptionService is extended for this use case
        try {
            return objectMapper.writeValueAsBytes(details);
        } catch (JsonProcessingException e) {
            throw new InvalidMappingException("Failed to encrypt bytes", e);
        }
    }

    private byte[] decryptBlob(byte[] blob) {
        // TODO: temporary solution until EncryptionService is extended for this use case
        try {
            return blob;
        } catch (Exception e) {
            throw new InvalidMappingException("Failed to decrypt bytes", e);
        }
    }
}
