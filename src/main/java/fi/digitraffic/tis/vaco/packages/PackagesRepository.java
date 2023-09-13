package fi.digitraffic.tis.vaco.packages;

import fi.digitraffic.tis.vaco.db.RowMappers;
import fi.digitraffic.tis.vaco.packages.model.ImmutablePackage;
import fi.digitraffic.tis.vaco.packages.model.Package;
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

    public Optional<Package> findPackage(String entryPublicId, String packageName) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                      FROM package
                     WHERE entry_id = (SELECT id FROM entry WHERE public_id = ?)
                       AND name = ?
                    """,
                RowMappers.PACKAGE,
                entryPublicId, packageName));
        } catch (EmptyResultDataAccessException erdae) {
            return Optional.empty();
        }
    }

    public ImmutablePackage createPackage(ImmutablePackage p) {
        return jdbc.queryForObject("""
                INSERT INTO package(entry_id, path, name)
                     VALUES (?, ?, ?)
                  RETURNING id, entry_id, name, path
                """,
            RowMappers.PACKAGE,
            p.entryId(), p.path(), p.name());
    }

    public List<ImmutablePackage> findPackages(Long entryId) {
        try {
            return jdbc.query("""
                SELECT *
                  FROM package
                 WHERE entry_id = ?
                """,
                RowMappers.PACKAGE,
                entryId);
        } catch (EmptyResultDataAccessException e) {
            return List.of();
        }
    }
}
