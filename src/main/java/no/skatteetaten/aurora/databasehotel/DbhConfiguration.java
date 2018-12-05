package no.skatteetaten.aurora.databasehotel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.google.common.base.Joiner;

import no.skatteetaten.aurora.databasehotel.utils.CollectionUtils;

@ConfigurationProperties(prefix = "database-config")
@Component
public class DbhConfiguration {

    private static final String[] REQUIRED_PARAMS = new String[] { "username", "password", "instanceName", "service",
        "clientService", "host" };
    private static final Logger LOGGER = LoggerFactory.getLogger(DbhConfiguration.class);
    private List<Map<String, Object>> databases;

    protected static void validateConfig(List<Map<String, Object>> databaseConfigs) {

        for (int i = 0; i < databaseConfigs.size(); i++) {
            Map<String, Object> db = databaseConfigs.get(i);
            for (String requiredParam : REQUIRED_PARAMS) {
                if (!db.containsKey(requiredParam)) {
                    throw new ConfigException(String.format(
                        "Required configuration parameter missing [%s] in configuration of database with index [%d]",
                        requiredParam, i));
                }
            }
        }
    }

    public List<Map<String, Object>> getDatabasesConfig() {

        if (databases == null) {
            // Just in case no databases have been configured.
            databases = new ArrayList<>();
        }
        validateConfig(databases);

        List<String> hosts = CollectionUtils.mapToList(databases, db -> (String) db.get("host"));
        String hostsString = Joiner.on(", ").join(hosts);
        LOGGER.info("Using databases [{}]", hostsString);

        return databases;
    }

    public List<Map<String, Object>> getDatabases() {
        return databases;
    }

    public void setDatabases(List<Map<String, Object>> databases) {
        this.databases = databases;
    }
}
