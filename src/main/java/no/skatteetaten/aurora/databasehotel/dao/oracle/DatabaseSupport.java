package no.skatteetaten.aurora.databasehotel.dao.oracle;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

public class DatabaseSupport {

    protected JdbcTemplate jdbcTemplate;

    public DatabaseSupport(DataSource dataSource) {

        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public <T> List<T> queryForMany(String query, Class<T> dtoType, Object... params) {

        return jdbcTemplate.query(query, new BeanPropertyRowMapper<>(dtoType), params);
    }

    public <T> Optional<T> queryForOne(String query, Class<T> dtoType, Object... params) {

        List<T> objects = queryForMany(query, dtoType, params);
        return objects.isEmpty() ? Optional.empty() : Optional.of(objects.get(0));
    }

    public Optional<Map<String, Object>> queryForFirstOrOptional(String sql, Object... params) {

        List<Map<String, Object>> objects = jdbcTemplate.queryForList(sql, params);
        return objects.isEmpty() ? Optional.empty() : Optional.of(objects.get(0));
    }
}
