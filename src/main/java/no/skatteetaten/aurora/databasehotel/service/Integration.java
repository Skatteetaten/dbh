package no.skatteetaten.aurora.databasehotel.service;

import java.time.Duration;

import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema;

public interface Integration {

    void onSchemaCreated(DatabaseSchema databaseSchema);

    void onSchemaDeleted(DatabaseSchema databaseSchema, Duration cooldownDuration);

    void onSchemaUpdated(DatabaseSchema databaseSchema);
}
