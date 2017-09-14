package no.skatteetaten.aurora.databasehotel.domain;

import no.skatteetaten.aurora.databasehotel.utils.Assert;

public class DatabaseSchemaMetaData {

    private final Double sizeInMb;

    public DatabaseSchemaMetaData(Double sizeInMb) {
        Assert.notNull(sizeInMb, "sizeInMb must be set");
        this.sizeInMb = sizeInMb;
    }

    public Double getSizeInMb() {
        return sizeInMb;
    }
}
