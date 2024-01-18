package fi.digitraffic.tis.vaco.company.repository;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.Hierarchy;
import fi.digitraffic.tis.vaco.company.model.ImmutableHierarchy;
import fi.digitraffic.tis.vaco.company.model.IntermediateHierarchyLink;
import fi.digitraffic.tis.vaco.db.ArraySqlValue;
import fi.digitraffic.tis.vaco.db.RowMappers;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class CompanyRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public CompanyRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
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
        /*
        Implementation note: Generally I push logic like this to database, but in this case my mind kinda broke on
        recursive hierarchies querying. If you come up with an improvement, please feel free to modify this!
         */
        List<IntermediateHierarchyLink> flatLinks = jdbc.query("""
            WITH RECURSIVE
                hierarchy AS (SELECT root.id AS child_id, NULL::BIGINT AS parent_id
                                FROM root
                               UNION ALL
                              SELECT ps.partner_b_id, ps.partner_a_id
                                FROM hierarchy
                                         JOIN
                                     partnership ps
                                     ON ps.partner_a_id = hierarchy.child_id),
                root AS (SELECT id FROM company WHERE business_id = ?)
          SELECT DISTINCT *
            FROM hierarchy
             """, RowMappers.INTERMEDIATE_HIERARCHY_LINK, company.businessId());

        Set<Long> allCompanyIds = new HashSet<>();
        flatLinks.forEach(link -> {
            if (link.parentId() != null) {
                allCompanyIds.add(link.parentId());
            }
            if (link.childId() != null) {
                allCompanyIds.add(link.childId());
            }
        });

        Map<Long, Company> companiesbyId = findAlLByIds(List.copyOf(allCompanyIds));

        Map<Long, List<IntermediateHierarchyLink>> parentsAndChildren = flatLinks.stream()
            .filter(l -> l.parentId() != null)
            .collect(Collectors.groupingBy(IntermediateHierarchyLink::parentId));

        ImmutableHierarchy.Builder builder = ImmutableHierarchy.builder();
        resolveHierarchy(company, builder, companiesbyId, parentsAndChildren);
        return builder.build();
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
    /*
    namedJdbc.query("""
            SELECT DISTINCT *
              FROM company c
             WHERE ad_group_id IN (:adGroupIds)
            """,

            RowMappers.COMPANY));
     */
}
