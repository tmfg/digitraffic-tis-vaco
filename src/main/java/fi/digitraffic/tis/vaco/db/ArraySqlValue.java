package fi.digitraffic.tis.vaco.db;

import org.springframework.jdbc.core.StatementCreatorUtils;
import org.springframework.jdbc.support.SqlValue;

import java.sql.Array;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;

/**
 * Utility for inserting arrays with Spring's <code>(NamedParameter)JdbcTemplate</code>.
 *
 * @see <a href="https://stackoverflow.com/a/55398179/44523">original from StackOverflow, published under Public Domain</a>
 */
public class ArraySqlValue implements SqlValue {
    private final Object[] arr;
    private final String   dbTypeName;

    public static ArraySqlValue create(final Object[] arr) {
        return new ArraySqlValue(arr, determineDbTypeName(arr));
    }

    public static ArraySqlValue create(final Object[] arr, final String dbTypeName) {
        return new ArraySqlValue(arr, dbTypeName);
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] read(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        return (T[]) array.getArray();
    }

    private ArraySqlValue(final Object[] arr, final String dbTypeName) {
        this.arr = Objects.requireNonNull(arr);
        this.dbTypeName = Objects.requireNonNull(dbTypeName);
    }

    @Override
    public void setValue(final PreparedStatement ps, final int paramIndex) throws SQLException {
        final Array arrayValue = ps.getConnection().createArrayOf(dbTypeName, arr);
        ps.setArray(paramIndex, arrayValue);
    }

    @Override
    public void cleanup() {
        // no-op as this class is stateless
    }

    private static String determineDbTypeName(final Object[] arr) {
        // use Spring Utils similar to normal JdbcTemplate inner workings
        final int sqlParameterType =
            StatementCreatorUtils.javaTypeToSqlParameterType(arr.getClass().getComponentType());
        final JDBCType jdbcTypeToUse = JDBCType.valueOf(sqlParameterType);
        // lowercasing typename for Postgres
        final String typeNameToUse = jdbcTypeToUse.getName().toLowerCase(Locale.US);
        return typeNameToUse;
    }
}
