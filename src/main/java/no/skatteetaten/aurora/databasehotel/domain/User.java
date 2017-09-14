package no.skatteetaten.aurora.databasehotel.domain;

public class User {

    private final String id;

    private final String name;

    private final String password;

    private final String type;

    public User(String id, String name, String password, String type) {

        this.id = id;
        this.name = name;
        this.password = password;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    public String getType() {
        return type;
    }
}
