package fi.digitraffic.tis.vaco.db.repositories;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.db.model.PackageRecord;
import fi.digitraffic.tis.vaco.db.model.TaskRecord;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.model.Task;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PackageRepository {

    private final JdbcTemplate jdbc;

    public PackageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PackageRecord> findPackage(TaskRecord task, String packageName) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                      FROM package
                     WHERE task_id = ?
                       AND name = ?
                    """,
                RowMappers.PACKAGE_RECORD,
                task.id(), packageName));
        } catch (EmptyResultDataAccessException erdae) {
            return Optional.empty();
        }
    }

    public PackageRecord upsertPackage(Package p) {
        return jdbc.queryForObject("""
                INSERT INTO package(task_id, path, name)
                     VALUES (?, ?, ?)
                ON CONFLICT (task_id, name)
                         DO UPDATE SET path = excluded.path
                  RETURNING *
                """,
            RowMappers.PACKAGE_RECORD,
            p.task().id(),
            p.path(),
            p.name());
    }

    public List<PackageRecord> findPackages(Task task) {
        try {
            return jdbc.query("""
                SELECT *
                  FROM package
                 WHERE task_id = ?
                """,
                RowMappers.PACKAGE_RECORD,
                task.id());
        } catch (EmptyResultDataAccessException e) {
            return List.of();
        }
    }
}
