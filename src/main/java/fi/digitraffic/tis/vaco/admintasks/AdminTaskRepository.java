package fi.digitraffic.tis.vaco.admintasks;

import fi.digitraffic.tis.vaco.admintasks.model.GroupIdMappingTask;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.db.RowMappers;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class AdminTaskRepository {

    private final JdbcTemplate jdbc;

    private final CompanyRepository companyRepository;

    public AdminTaskRepository(JdbcTemplate jdbc, CompanyRepository companyRepository) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.companyRepository = Objects.requireNonNull(companyRepository);
    }

    public GroupIdMappingTask create(GroupIdMappingTask task) {
        try {
            return jdbc.queryForObject("""
            INSERT INTO admin_groupid (group_id)
                 VALUES (?)
            ON CONFLICT (group_id) DO NOTHING
            RETURNING *
            """,
            RowMappers.ADMIN_GROUPID,
            task.groupId());
        }catch (EmptyResultDataAccessException erdae) {
            return findByGroupId(task.groupId());
        }
    }

    private GroupIdMappingTask findByGroupId(String groupId) {
        return jdbc.queryForObject("""
            SELECT *
              FROM admin_groupid
             WHERE group_id = ?
            """,
            RowMappers.ADMIN_GROUPID,
            groupId);
    }

    @Transactional
    public Pair<GroupIdMappingTask, CompanyRecord> resolve(GroupIdMappingTask task, Company company) {
        return companyRepository.findByBusinessId(company.businessId())
            .map(c -> {
                GroupIdMappingTask updatedTask = jdbc.queryForObject(
                    """
                       UPDATE admin_groupid
                          SET completed = NOW()
                        WHERE id = ? OR public_id = ?
                    RETURNING *
                    """,
                    RowMappers.ADMIN_GROUPID,
                    task.id(),
                    task.publicId());
                CompanyRecord updatedCompany = companyRepository.updateAdGroupId(c, task.groupId());

                return Pair.of(updatedTask, updatedCompany);
            }).orElse(null);
    }

    public GroupIdMappingTask resolveSkipped(GroupIdMappingTask task) {
        return jdbc.queryForObject("""
               UPDATE admin_groupid
                  SET skip = true,
                      completed = NOW()
                WHERE id = ? OR public_id = ?
            RETURNING *
            """,
            RowMappers.ADMIN_GROUPID,
            task.id(),
            task.publicId());
    }

    public List<GroupIdMappingTask> findAllOpenGroupIdTasks() {
        return jdbc.query("""
            SELECT *
              FROM admin_groupid
             WHERE skip = FALSE
               AND completed IS NULL
            """,
            RowMappers.ADMIN_GROUPID);
    }

    public boolean deleteGroupIdMappingTask(GroupIdMappingTask task) {
        return jdbc.update("""
            DELETE FROM admin_groupid
             WHERE id = ?
                OR public_id = ?
            """,
            task.id(),
            task.publicId()) == 1;
    }

    public Optional<GroupIdMappingTask> findGroupIdTaskByPublicId(String publicId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT *
                  FROM admin_groupid
                 WHERE public_id = ?
                """,
                RowMappers.ADMIN_GROUPID,
                publicId));
        } catch (EmptyResultDataAccessException erdae) {
            return Optional.empty();
        }
    }
}
