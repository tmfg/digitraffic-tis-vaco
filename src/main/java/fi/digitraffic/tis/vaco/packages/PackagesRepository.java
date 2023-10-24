package fi.digitraffic.tis.vaco.packages;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.packages.model.ImmutablePackage;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.model.Task;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PackagesRepository {

    private final JdbcTemplate jdbc;

    public PackagesRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Package> findPackage(Task task, String packageName) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                      FROM package
                     WHERE task_id = ?
                       AND name = ?
                    """,
                RowMappers.PACKAGE,
                task.id(), packageName));
        } catch (EmptyResultDataAccessException erdae) {
            return Optional.empty();
        }
    }

    public ImmutablePackage createPackage(ImmutablePackage p) {
        return jdbc.queryForObject("""
                INSERT INTO package(task_id, path, name)
                     VALUES (?, ?, ?)
                  RETURNING id, task_id, name, path
                """,
            RowMappers.PACKAGE,
            p.taskId(), p.path(), p.name());
    }

    public List<ImmutablePackage> findPackages(Task task) {
        try {
            return jdbc.query("""
                SELECT *
                  FROM package
                 WHERE task_id = ?
                """,
                RowMappers.PACKAGE,
                task.id());
        } catch (EmptyResultDataAccessException e) {
            return List.of();
        }
    }
}
