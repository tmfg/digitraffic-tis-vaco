package fi.digitraffic.tis.vaco.admintasks;

import fi.digitraffic.tis.vaco.admintasks.model.GroupIdMappingTask;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.db.RowMappers;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class AdminTaskRepository {

    private final JdbcTemplate jdbc;

    public AdminTaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
    public Pair<GroupIdMappingTask, Company> resolve(GroupIdMappingTask task, Company company) {
        GroupIdMappingTask updatedTask = jdbc.queryForObject("""
                UPDATE admin_groupid
                   SET completed = NOW()
                 WHERE id = ? OR public_id = ?
             RETURNING *
            """,
            RowMappers.ADMIN_GROUPID,
            task.id(),
            task.publicId());

        Company updatedCompany = jdbc.queryForObject("""
                UPDATE company
                   SET ad_group_id = ?
                 WHERE id = ?
             RETURNING *
            """,
            RowMappers.COMPANY,
            task.groupId(),
            company.id());

        return Pair.of(updatedTask, updatedCompany);
    }

    public GroupIdMappingTask resolveSkipped(GroupIdMappingTask task) {
        return jdbc.queryForObject("""
               UPDATE admin_groupid
                  SET skip = true
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
}
