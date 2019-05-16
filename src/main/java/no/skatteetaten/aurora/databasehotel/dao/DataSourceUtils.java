package no.skatteetaten.aurora.databasehotel.dao;

import static com.google.common.base.Strings.nullToEmpty;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public final class DataSourceUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceUtils.class);

    private DataSourceUtils() {

    }

    public static HikariDataSource createDataSource(String jdbcUrl, String username, String password) {

        return createDataSource(jdbcUrl, username, password, 2);
    }

    public static HikariDataSource createDataSource(String jdbcUrl, String username, String password,
        int maximumPoolSize) {

        HikariConfig config = createConfig(jdbcUrl, username, password, maximumPoolSize);

        return createDataSource(config);
    }

    public static HikariDataSource createDataSource(HikariConfig config) {

        String maskedPassword = createPasswordHint(config.getPassword());
        LOGGER
            .debug("Creating datasource using jdbcUrl: \"{}\", username: \"{}\", password: \"{}\"", config.getJdbcUrl(),
                config.getUsername(), maskedPassword);

        return new HikariDataSource(config);
    }

    public static HikariConfig createConfig(String jdbcUrl, String username, String password, int maximumPoolSize) {

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maximumPoolSize);

        // Just some defaults. Has not been properly evaluated.
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        return config;
    }

    static String createPasswordHint(String password) {

        if (nullToEmpty(password).length() < 8) {
            return StringUtils.repeat("*", nullToEmpty(password).length());
        }
        return password.substring(0, 2) + StringUtils.repeat("*", password.length() - 4) + password
            .substring(password.length() - 2);
    }
}
