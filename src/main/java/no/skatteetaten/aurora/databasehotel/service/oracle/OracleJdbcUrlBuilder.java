package no.skatteetaten.aurora.databasehotel.service.oracle;

import no.skatteetaten.aurora.databasehotel.service.JdbcUrlBuilder;

public class OracleJdbcUrlBuilder implements JdbcUrlBuilder {
    private final String service;

    public OracleJdbcUrlBuilder(String service) {
        this.service = service;
    }

    @Override
    public String create(String dbHost, int port) {

        return String.format("jdbc:oracle:thin:@%s:%d/%s", dbHost, port, service);
    }
}
