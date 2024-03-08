package fi.digitraffic.tis.vaco.company.repository;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.Hierarchy;
import fi.digitraffic.tis.vaco.company.model.ImmutableHierarchy;
import fi.digitraffic.tis.vaco.company.model.IntermediateHierarchyLink;
import fi.digitraffic.tis.vaco.company.model.Partnership;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.db.ArraySqlValue;
import fi.digitraffic.tis.vaco.db.RowMappers;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class CompanyHierarchyRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public CompanyHierarchyRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.namedJdbc = Objects.requireNonNull(namedJdbc);
    }

    public Company create(Company company) {
        return jdbc.queryForObject("""
                INSERT INTO company(business_id, name, contact_emails)
                     VALUES (?, ?, ?)
                  RETURNING *
                """,
                RowMappers.COMPANY,
                company.businessId(),
            company.name(),
            ArraySqlValue.create(company.contactEmails().toArray(new String[0])));
    }

    public Company update(String businessId, Company company) {
        return jdbc.queryForObject("""
                UPDATE company
                    SET name = ?, language = (?)::company_language, ad_group_id = ?, contact_emails = ?
                    WHERE business_id = ?
                  RETURNING *
                """,
            RowMappers.COMPANY,
            company.name(),
            company.language(),
            company.adGroupId(),
            ArraySqlValue.create(company.contactEmails().toArray(new String[0])),
            businessId);
    }

    public Optional<Company> findByBusinessId(String businessId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                      FROM company
                     WHERE business_id = ?
                    """,
                RowMappers.COMPANY,
                businessId));
        } catch (EmptyResultDataAccessException erdae) {
            return Optional.empty();
        }
    }

    public void delete(String businessId) {
        jdbc.update("DELETE FROM company WHERE business_id = ?", businessId);
    }

    public List<Company> listAllWithEntries() {
        return jdbc.query("""
            SELECT *
              FROM company o
             WHERE o.business_id IN (SELECT DISTINCT e.business_id
                                       FROM entry e)
            """,
            RowMappers.COMPANY);
    }

    public Set<Company> findAll() {
        return Set.copyOf(jdbc.query("""
            SELECT DISTINCT *
              FROM company c
            """,
            RowMappers.COMPANY));
    }

    public Set<Company> findAllByAdGroupIds(List<String> adGroupIds) {
        if (adGroupIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(namedJdbc.query("""
            SELECT DISTINCT *
              FROM company c
             WHERE ad_group_id IN (:adGroupIds)
            """,
            new MapSqlParameterSource()
                .addValue("adGroupIds", adGroupIds),
            RowMappers.COMPANY));
    }

    public Company updateAdGroupId(Company company, String adGroupId) {
        return jdbc.queryForObject("""
                UPDATE company
                   SET ad_group_id = ?
                 WHERE id = ?
             RETURNING *
            """,
            RowMappers.COMPANY,
            adGroupId,
            company.id());
    }

    @Transactional
    public Hierarchy resolveDescendantHierarchyFor(Company company) {
        return null;// loadHierarchies();
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

        // 2b. create lookup for id->entity
        Map<Long, Company> companiesbyId = hierarchyLinks.stream().collect(
            Collectors.toMap(
                IntermediateHierarchyLink::childId,
                IntermediateHierarchyLink::company,
                (company1, company2) -> company2));

        // 2c. create lookup for getting children of each parent
        Map<Long, List<IntermediateHierarchyLink>> parentsAndChildren = hierarchyLinks.stream()
            .filter(l -> l.parentId() != null)
            .collect(Collectors.groupingBy(IntermediateHierarchyLink::parentId));

        // 2d. construct the hierarchy
        return buildHierarchy(companiesbyId.get(rootId), companiesbyId, parentsAndChildren);
    }

    private static Hierarchy buildHierarchy(Company company,
                                            Map<Long, Company> companiesbyId,
                                            Map<Long, List<IntermediateHierarchyLink>> parentsAndChildren) {
        ImmutableHierarchy.Builder builder = ImmutableHierarchy.builder();
        resolveHierarchy(company, builder, companiesbyId, parentsAndChildren);
        return builder.build();
    }

    private List<IntermediateHierarchyLink> findHierarchyLinks(Long root) {
        return jdbc.query("""
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
            LEFT JOIN company c ON h.child_id = c.id
             """,
            RowMappers.INTERMEDIATE_HIERARCHY_LINK,
            root);
    }

    private Set<Long> findHierarchyRoots() {
        return Set.copyOf(jdbc.queryForList("""
                SELECT DISTINCT id FROM company c WHERE c.id NOT IN (SELECT DISTINCT partner_b_id FROM partnership)
                """,
            Long.class));
    }

    private static void resolveHierarchy(Company parent, ImmutableHierarchy.Builder builder, Map<Long, Company> companiesbyId, Map<Long, List<IntermediateHierarchyLink>> parentsAndChildren) {
        builder.company(parent);
        List<IntermediateHierarchyLink> children = parentsAndChildren.getOrDefault(parent.id(), List.of());
        builder.addAllChildren(Streams.collect(children, child -> {
                ImmutableHierarchy.Builder b = ImmutableHierarchy.builder();
                b.company(companiesbyId.get(child.parentId()));
                if (child.childId() != null) {
                    resolveHierarchy(companiesbyId.get(child.childId()), b, companiesbyId, parentsAndChildren);
                }
                return b.build();
            }));
    }

    private Map<Long, Company> findAlLByIds(List<Long> ids) {
        return Streams.collect(namedJdbc.query("""
            SELECT *
            FROM company
            WHERE id IN (:ids)
            """,
                new MapSqlParameterSource()
                    .addValue("ids", ids),
            RowMappers.COMPANY)
                ,
            Company::id, Function.identity());
    }

    public Map<String, Company> findAlLByIds() {
        return Streams.collect(jdbc.query("""
            SELECT *
            FROM company
            """,
                RowMappers.COMPANY)
            ,
            Company::businessId, Function.identity());
    }


    public Partnership create(Partnership partnership) {
        jdbc.update("""
                INSERT INTO partnership(type, partner_a_id, partner_b_id)
                     VALUES (?::partnership_type, ?, ?)
                """,
            partnership.type().fieldName(), partnership.partnerA().id(), partnership.partnerB().id());
        // There's no easy way to do this with just one query so the fetch query is delegated to #findByIds to avoid
        // code duplication. The blind call to Optional#get is on purpose.
        return findByIds(partnership.type(), partnership.partnerA().id(), partnership.partnerB().id()).get();
    }

    public void deletePartnership(Partnership partnership) {
        jdbc.update("DELETE FROM partnership WHERE type = ?::partnership_type AND partner_a_id =? AND partner_b_id = ?",
            partnership.type().fieldName(), partnership.partnerA().id(), partnership.partnerB().id());
    }

    public Optional<Partnership> findByIds(PartnershipType type,
                                           Long partnerAId,
                                           Long partnerBId) {
        return jdbc.query("""
                SELECT c.type AS type,
                       o_a.id as partner_a_id,
                       o_a.business_id as partner_a_business_id,
                       o_a.name as partner_a_name,
                       o_a.contact_emails as partner_a_contact_emails,
                       o_a.ad_group_id as partner_a_ad_group_id,
                       o_a.language as partner_a_language,
                       o_b.id as partner_b_id,
                       o_b.business_id as partner_b_business_id,
                       o_b.name as partner_b_name,
                       o_b.contact_emails as partner_b_contact_emails,
                       o_b.ad_group_id as partner_b_ad_group_id,
                       o_b.language as partner_b_language
                  FROM partnership c
                  JOIN company o_a ON c.partner_a_id = o_a.id
                  JOIN company o_b ON c.partner_b_id = o_b.id
                 WHERE c.type = ?::partnership_type AND c.partner_a_id = ? AND c.partner_b_id = ?
                """,
            RowMappers.PARTNERSHIP,
            type.fieldName(), partnerAId, partnerBId).stream().findFirst();
    }
}
