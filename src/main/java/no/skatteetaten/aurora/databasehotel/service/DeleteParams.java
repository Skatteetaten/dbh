package no.skatteetaten.aurora.databasehotel.service;

import java.time.Duration;

public class DeleteParams {
    private Duration cooldownDuration;

    private boolean assertExists;

    public DeleteParams(Duration cooldownDuration) {
        this(cooldownDuration, true);
    }

    public DeleteParams(Duration cooldownDuration, boolean assertExists) {
        this.cooldownDuration = cooldownDuration;
        this.assertExists = assertExists;
    }

    public Duration getCooldownDuration() {
        return cooldownDuration;
    }

    public boolean isAssertExists() {
        return assertExists;
    }
}
