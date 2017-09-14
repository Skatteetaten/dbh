package no.skatteetaten.aurora.databasehotel.service;

public interface JdbcUrlBuilder {

    String create(String dbHost, int port);
}
