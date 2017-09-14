package no.skatteetaten.aurora.databasehotel.dao.dto;

import java.util.Date;

public class ExternalSchema {

    private Date createdDate;

    private String jdbcUrl;

    public ExternalSchema() {
    }

    public ExternalSchema(Date createdDate, String jdbcUrl) {
        this.createdDate = createdDate;
        this.jdbcUrl = jdbcUrl;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }
}
