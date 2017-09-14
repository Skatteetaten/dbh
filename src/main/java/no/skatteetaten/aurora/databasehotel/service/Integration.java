package no.skatteetaten.aurora.databasehotel.service;

import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema;

public interface Integration {
    void onSchemaCreated(DatabaseSchema databaseSchema);

    void onSchemaDeleted(DatabaseSchema databaseSchema);

    void onSchemaUpdated(DatabaseSchema databaseSchema);
}
