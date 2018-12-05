package no.skatteetaten.aurora.databasehotel;

import static java.lang.String.format;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EnvVarMapper {

    /**
     * It bugs me quite a bit to have to have this method. The issue it resolves is that we need to support configuring
     * several database instances from environment vars. Spring boot natively supports this via indexes in the
     * environment variable names, but bash, unfortunately, does not support setting environment variables with such
     * names. Example:
     * <p>
     * <pre>
     * DATABASE_CONFIG_DATABASES[0]host=dbhost.example.com
     * DATABASE_CONFIG_DATABASES[0]service=dbhotel
     * DATABASE_CONFIG_DATABASES[0]instanceName=test-dev
     * DATABASE_CONFIG_DATABASES[1]host=dbhost2.example.com
     * DATABASE_CONFIG_DATABASES[1]service=dbhotel
     * DATABASE_CONFIG_DATABASES[1]instanceName=test-dev
     * </pre>
     * <p>
     * So, to support this, we need to derive a custom environment variable naming scheme and map that to spring boot
     * configuration properties. We could create new environment variable names from our custom scheme to the spring
     * boot supported array index form, but setting environment variables for the current process from Java is not
     * supported without reflection hacks. So, alas, we will derive a custom environment variable naming scheme and
     * map those directly to system properties.
     * <p>
     * Our naming scheme will match as closely as possible the desired (unsupported) format
     * <pre>
     * DATABASE_CONFIG_DATABASES_0_host=dbhost.example.com
     * DATABASE_CONFIG_DATABASES_0_service=dbhotel
     * DATABASE_CONFIG_DATABASES_0_instanceName=test-dev
     * DATABASE_CONFIG_DATABASES_1_host=dbhost2.example.com
     * DATABASE_CONFIG_DATABASES_1_service=dbhotel
     * DATABASE_CONFIG_DATABASES_1_instanceName=test-dev
     * </pre>
     */
    static void mapEnvironmentVarsToSystemProperties() {

        class DbConfig {
            String index, propertyName, value;
        }
        Pattern pattern = Pattern.compile("DATABASE_?CONFIG_DATABASES_(\\d+)_(.*)");

        Map<String, String> environmentVars = System.getenv();
        List<DbConfig> databaseConfigs = environmentVars.keySet().stream()
            .map(name -> {
                Matcher matcher = pattern.matcher(name);
                if (!matcher.find()) {
                    return null;
                }

                return new DbConfig() {
                    {
                        index = matcher.group(1);
                        propertyName = matcher.group(2);
                        value = System.getenv(name);
                    }
                };
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        databaseConfigs.forEach(dbConfig -> {
            String propertyName = format("database-config.databases[%s].%s", dbConfig.index, dbConfig.propertyName);
            System.setProperty(propertyName, dbConfig.value);
        });
    }
}
