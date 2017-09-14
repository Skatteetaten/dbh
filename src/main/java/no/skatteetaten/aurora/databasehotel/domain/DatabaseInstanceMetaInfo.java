package no.skatteetaten.aurora.databasehotel.domain;

public class DatabaseInstanceMetaInfo {

    private final String host;

    private final String instanceName;

    private final int port;

    public DatabaseInstanceMetaInfo(String instanceName, String host, int port) {

        this.instanceName = instanceName;
        this.host = host;
        this.port = port;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
