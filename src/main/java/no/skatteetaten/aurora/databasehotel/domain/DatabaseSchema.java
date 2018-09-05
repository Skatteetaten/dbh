package no.skatteetaten.aurora.databasehotel.domain;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DatabaseSchema {

    private final String id;
    private final Type type;
    private final DatabaseInstanceMetaInfo databaseInstanceMetaInfo;
    private final String jdbcUrl;
    private final String name;
    private final Date createdDate;
    private final Date lastUsedDate;
    private final DatabaseSchemaMetaData metaData;
    private final Set<User> users = new HashSet<>();
    private final Map<String, String> labels = new HashMap<>();

    public DatabaseSchema(String id, DatabaseInstanceMetaInfo databaseInstanceMetaInfo, String jdbcUrl, String name,
        Date createdDate, Date lastUsedDate, DatabaseSchemaMetaData metaData) {

        this(id, databaseInstanceMetaInfo, jdbcUrl, name, createdDate, lastUsedDate, metaData, Type.MANAGED);
    }

    public DatabaseSchema(String id, DatabaseInstanceMetaInfo databaseInstanceMetaInfo, String jdbcUrl, String name,
        Date createdDate, Date lastUsedDate, DatabaseSchemaMetaData metaData, Type type) {

        this.id = id;
        this.databaseInstanceMetaInfo = databaseInstanceMetaInfo;
        this.jdbcUrl = jdbcUrl;
        this.name = name;
        this.createdDate = createdDate;
        this.lastUsedDate = lastUsedDate;
        this.metaData = metaData;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public DatabaseInstanceMetaInfo getDatabaseInstanceMetaInfo() {
        return databaseInstanceMetaInfo;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getName() {
        return name;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public Date getLastUsedDate() {
        return lastUsedDate;
    }

    public Date getLastUsedOrCreatedDate() {
        return lastUsedDate != null ? lastUsedDate : createdDate;
    }

    public void addUser(User user) {

        this.users.removeIf(u -> u.getName().equals(user.getName()));
        this.users.add(user);
    }

    public Set<User> getUsers() {

        return new HashSet<>(users);
    }

    public Map<String, String> getLabels() {

        Map<String, String> lbls = new HashMap<>();
        lbls.putAll(this.labels);
        return lbls;
    }

    public void setLabels(Map<String, String> labels) {

        this.labels.clear();
        if (labels == null) {
            return;
        }
        this.labels.putAll(labels);
    }

    public boolean isUnused() {

        return lastUsedDate == null;
    }

    public Optional<DatabaseSchemaMetaData> getMetaData() {

        return Optional.ofNullable(metaData);
    }

    public Type getType() {
        return type;
    }

    public double getSizeMb() {

        return getMetaData()
            .map(DatabaseSchemaMetaData::getSizeInMb)
            .orElse(0.0);
    }

    public enum Type {
        MANAGED,
        EXTERNAL
    }
}
