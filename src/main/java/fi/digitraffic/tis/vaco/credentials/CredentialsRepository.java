package fi.digitraffic.tis.vaco.credentials;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.credentials.model.AuthenticationDetails;
import fi.digitraffic.tis.vaco.credentials.model.Credentials;
import fi.digitraffic.tis.vaco.credentials.model.CredentialsType;
import fi.digitraffic.tis.vaco.crypt.EncryptionService;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.CredentialsRecord;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
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

    private final EncryptionService encryptionService;

    public CredentialsRepository(JdbcTemplate jdbc, ObjectMapper objectMapper, EncryptionService encryptionService) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.encryptionService = Objects.requireNonNull(encryptionService);
    }

    public Optional<CredentialsRecord> createCredentials(Credentials credentials, CompanyRecord owner) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                """
                INSERT INTO credentials(owner_id, name, description, type, details, url_pattern)
                     VALUES (?, ?, ?, ?::credentials_type, ?, ?)
                  RETURNING id,
                            public_id,
                            owner_id,
                            name,
                            type,
                            description,
                            details,
                            url_pattern
                """,
                RowMappers.CREDENTIALS_RECORD(encryptionService::decryptBlob),
                owner.id(),
                credentials.name(),
                credentials.description(),
                credentials.type().fieldName(),
                encryptionService.encryptBlob(credentials.details()),
                credentials.urlPattern()
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
                RowMappers.CREDENTIALS_RECORD(encryptionService::decryptBlob),
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
                RowMappers.CREDENTIALS_RECORD(encryptionService::decryptBlob),
                publicId));
        } catch (EmptyResultDataAccessException erdae) {
            logger.warn("No credentials available by publicId {}", publicId, erdae);
            return Optional.empty();
        }
    }
    public CredentialsRecord updateCredentials(CredentialsRecord previous, CredentialsType type, String name, String description, AuthenticationDetails details, String urlPattern) {
        return jdbc.queryForObject(
            """
               UPDATE credentials
                  SET name = ?,
                      description = ?,
                      type = ?::credentials_type,
                      details = ?,
                      url_pattern = ?
                WHERE id = ?
            RETURNING *
            """,
            RowMappers.CREDENTIALS_RECORD(encryptionService::decryptBlob),
            name,
            description,
            type.fieldName(),
            encryptionService.encryptBlob(details),
            urlPattern,
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

    public Optional<CredentialsRecord> findForEntry(EntryRecord entry) {
        if (entry.credentials() == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                """
                SELECT c.*
                  FROM credentials c
                 WHERE c.id = ?
                """,
                RowMappers.CREDENTIALS_RECORD(encryptionService::decryptBlob),
                entry.credentials()));
        } catch (DataAccessException dae) {
            logger.warn("Failed to find credentials record for credentials %s/%s".formatted(entry.publicId(), entry.credentials()), dae);
            return Optional.empty();
        }
    }


}
