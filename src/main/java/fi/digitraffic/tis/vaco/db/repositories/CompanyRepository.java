package fi.digitraffic.tis.vaco.db.repositories;

import fi.digitraffic.tis.exceptions.PersistenceException;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.Hierarchy;
import fi.digitraffic.tis.vaco.company.model.ImmutableHierarchy;
import fi.digitraffic.tis.vaco.company.model.IntermediateHierarchyLink;
import fi.digitraffic.tis.vaco.company.service.model.CompanyRole;
import fi.digitraffic.tis.vaco.db.ArraySqlValue;
import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class CompanyRepository {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JdbcTemplate jdbc;

    private final NamedParameterJdbcTemplate namedJdbc;

    private final RecordMapper recordMapper;

    private final CachingService cachingService;

    public CompanyRepository(JdbcTemplate jdbc,
                             NamedParameterJdbcTemplate namedJdbc,
                             RecordMapper recordMapper,
                             CachingService cachingService) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.namedJdbc = Objects.requireNonNull(namedJdbc);
        this.recordMapper = Objects.requireNonNull(recordMapper);
        this.cachingService = Objects.requireNonNull(cachingService);
    }

    public Optional<CompanyRecord> create(Company company) {
        return cachingService.cacheCompanyRecord(company.businessId(), key -> {
            return jdbc.queryForObject(
                """
                INSERT INTO company(business_id, name, contact_emails, publish)
                     VALUES (?, ?, ?, ?)
                  RETURNING *
                """,
                RowMappers.COMPANY_RECORD,
                company.businessId(),
                company.name(),
                ArraySqlValue.create(company.contactEmails().toArray(new String[0])),
                company.publish());
        });
    }

    public CompanyRecord update(String businessId, Company company) {
        CompanyRecord companyRecord = jdbc.queryForObject(
            """
                   UPDATE company
                      SET name = ?,
                          language = (?)::company_language,
                          ad_group_id = ?,
                          contact_emails = ?,
                          publish = ?,
                          codespaces = ?,
                          notification_webhook_uri = ?,
                          website = ?,
                          roles = ?
                    WHERE business_id = ?
                RETURNING *
                """,
            RowMappers.COMPANY_RECORD,
            company.name(),
            company.language(),
            company.adGroupId(),
            ArraySqlValue.create(company.contactEmails().toArray(new String[0])),
            company.publish(),
            ArraySqlValue.create(company.codespaces().toArray(new String[0])),
            company.notificationWebhookUri(),
            company.website(),
            ArraySqlValue.create(company.roles().stream().map(CompanyRole::fieldName).toArray(String[]::new)),
            businessId);
        cachingService.invalidateCompanyRecord(company.businessId());
        return findById(companyRecord.id());
    }

    public Optional<CompanyRecord> findByBusinessId(String businessId) {
        return cachingService.cacheCompanyRecord(businessId, key -> {
            try {
                return jdbc.queryForObject(
                    """
                    SELECT *
                      FROM company
                     WHERE business_id = ?
                    """,
                    RowMappers.COMPANY_RECORD,
                    businessId);
            } catch (EmptyResultDataAccessException erdae) {
                return null;
            }
        });
    }

    public List<CompanyRecord> listAllWithEntries() {
        return jdbc.query("""
            SELECT *
              FROM company c
             WHERE c.business_id IN (SELECT DISTINCT e.business_id
                                       FROM entry e)
            """,
            RowMappers.COMPANY_RECORD);
    }

    public Optional<CompanyRecord> findByAdGroupId(String groupId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                """
                SELECT *
                  FROM company c
                 WHERE c.ad_group_id = ?
                """,
                RowMappers.COMPANY_RECORD,
                groupId));
        } catch (EmptyResultDataAccessException erdae) {
            return Optional.empty();
        }
    }

    public CompanyRecord updateAdGroupId(CompanyRecord company, String adGroupId) {
        CompanyRecord updated = jdbc.queryForObject(
            """
                    UPDATE company
                       SET ad_group_id = ?
                     WHERE id = ?
                 RETURNING *
                """,
            RowMappers.COMPANY_RECORD,
            adGroupId,
            company.id());
        return cachingService.updateCompanyRecord(updated);
    }

    public CompanyRecord updateContactEmails(CompanyRecord company, List<String> replacementEmails) {
        CompanyRecord updated = jdbc.queryForObject(
            """
                UPDATE company
                   SET contact_emails = ?
                 WHERE id = ?
             RETURNING *
            """,
            RowMappers.COMPANY_RECORD,
            ArraySqlValue.create(replacementEmails.toArray(new String[0])),
            company.id());
        return cachingService.updateCompanyRecord(updated);
    }

    public Map<Company, Hierarchy> findRootHierarchies() {
        // 1. find roots
        Set<Long> roots = findHierarchyRoots();

        Map<Company, Hierarchy> hierarchies = new HashMap<>();

        // 2. build hierarchies
        roots.forEach(root -> {
            Hierarchy hierarchy = loadHierarchy(root);
            hierarchies.put(hierarchy.company(), hierarchy);
        });
        return hierarchies;
    }

    private Hierarchy loadHierarchy(Long rootId) {
        // 2a. find hierarchy links for root
        List<IntermediateHierarchyLink> hierarchyLinks = findHierarchyLinks(rootId);

        // 2b. load all distinct companies in hierarchy
        Set<Long> companyIds = Streams.concat(
                Streams.map(hierarchyLinks, IntermediateHierarchyLink::parentId).stream(),
                Streams.map(hierarchyLinks, IntermediateHierarchyLink::childId).stream())
            .filter(Objects::nonNull)
            .toSet();
        List<CompanyRecord> companies = findAllByIds(companyIds);

        // 2c. create lookup for id->entity
        Map<Long, CompanyRecord> companiesbyId = companies.stream().collect(
            Collectors.toMap(
                CompanyRecord::id,
                Function.identity(),
                (company1, company2) -> company2));

        // 2d. create lookup for getting children of each parent
        Map<Long, List<IntermediateHierarchyLink>> parentsAndChildren = hierarchyLinks.stream()
            .filter(l -> l.parentId() != null)
            .collect(Collectors.groupingBy(IntermediateHierarchyLink::parentId));

        // 2e. construct the hierarchy
        return buildHierarchy(companiesbyId.get(rootId), companiesbyId, parentsAndChildren);
    }

    private List<CompanyRecord> findAllByIds(Set<Long> companyIds) {
        return namedJdbc.query(
            """
            SELECT DISTINCT *
              FROM company c
             WHERE id IN (:companyIds)
            """,
            new MapSqlParameterSource()
                .addValue("companyIds", companyIds),
            RowMappers.COMPANY_RECORD);
    }

    private Hierarchy buildHierarchy(CompanyRecord company,
                                     Map<Long, CompanyRecord> companiesbyId,
                                     Map<Long, List<IntermediateHierarchyLink>> parentsAndChildren) {
        ImmutableHierarchy.Builder builder = ImmutableHierarchy.builder();
        resolveHierarchy(company, builder, companiesbyId, parentsAndChildren);
        return builder.build();
    }

    private List<IntermediateHierarchyLink> findHierarchyLinks(Long root) {
        return jdbc.query(
            """
              WITH RECURSIVE
                  hierarchy AS (SELECT id AS child_id, NULL::BIGINT AS parent_id
                                  FROM root
                                 UNION ALL
                                SELECT ps.partner_b_id, ps.partner_a_id
                                  FROM hierarchy
                                           JOIN
                                       partnership ps
                                       ON ps.partner_a_id = hierarchy.child_id),
                  root AS (SELECT ? AS id)
            SELECT DISTINCT *
              FROM hierarchy h
            """,
            RowMappers.INTERMEDIATE_HIERARCHY_LINK,
            root);
    }

    private Set<Long> findHierarchyRoots() {
        return Set.copyOf(jdbc.queryForList(
            """
            SELECT DISTINCT id
              FROM company c
             WHERE c.id NOT IN (SELECT DISTINCT partner_b_id FROM partnership)
            """,
            Long.class));
    }

    private void resolveHierarchy(CompanyRecord parent,
                                  ImmutableHierarchy.Builder builder,
                                  Map<Long, CompanyRecord> companiesbyBusinessId,
                                  Map<Long, List<IntermediateHierarchyLink>> parentsAndChildren) {
        builder.company(recordMapper.toCompany(parent));
        List<IntermediateHierarchyLink> children = parentsAndChildren.getOrDefault(parent.id(), List.of());
        builder.addAllChildren(Streams.collect(children, child -> {
                ImmutableHierarchy.Builder b = ImmutableHierarchy.builder();
                b.company(recordMapper.toCompany(companiesbyBusinessId.get(child.parentId())));
                if (child.childId() != null) {
                    resolveHierarchy(companiesbyBusinessId.get(child.childId()), b, companiesbyBusinessId, parentsAndChildren);
                }
                return b.build();
            }));
    }

    public Map<String, CompanyRecord> findAllByIds() {
        return Streams.collect(
            jdbc.query(
                """
                SELECT *
                  FROM company
                """,
                RowMappers.COMPANY_RECORD),
            CompanyRecord::businessId,
            Function.identity());
    }

    public List<CompanyRecord> listAll() {
        return jdbc.query(
            """
            SELECT *
              FROM company
            """,
            RowMappers.COMPANY_RECORD);
    }

    public boolean deleteByBusinessId(String businessId) {
        if (jdbc.update("DELETE FROM company WHERE business_id = ?", businessId) > 0) {
            cachingService.invalidateCompanyRecord(businessId);
            return true;
        } else {
            logger.warn("Failed to delete company by businessId {}", businessId);
            return false;
        }
    }

    public CompanyRecord findById(long id) {
        try {
            return jdbc.queryForObject(
                "SELECT * FROM company WHERE id = ?",
                RowMappers.COMPANY_RECORD,
                id);
        } catch (DataAccessException dae) {
            throw new PersistenceException(String.format("Failed to load Company with id %s from database, possible data corruption!", id), dae);
        }
    }
}
