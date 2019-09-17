package no.skatteetaten.aurora.databasehotel.web.security;

import static java.lang.String.format;

import static com.google.common.base.Strings.isNullOrEmpty;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Component for reading the shared secret used for authentication. You may specify the shared secret directly using
 * the aurora.token.value property, or specify a file containing the secret with the aurora.token.location property.
 */
@Component
public class SharedSecretReader {

    private static Logger log = LoggerFactory.getLogger(SharedSecretReader.class);

    private final String secretLocation;

    private final String secretValue;

    @Autowired
    public SharedSecretReader(@Value("${aurora.token.location:}") String secretLocation,
        @Value("${aurora.token.value:}") String secretValue) {

        this.secretLocation = secretLocation;
        this.secretValue = secretValue;

        if (isNullOrEmpty(secretLocation) && isNullOrEmpty(secretValue)) {
            throw new IllegalArgumentException("Either aurora.token.location or aurora.token.value must be specified");
        }
    }

    public String getSecret() {

        if (!isNullOrEmpty(secretValue)) {
            return secretValue;
        }

        File secretFile = new File(secretLocation).getAbsoluteFile();
        try {
            log.info("Reading token from file {}", secretFile.getAbsolutePath());
            return new String(Files.readAllBytes(Paths.get(secretLocation)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                format("Unable to read shared secret from specified location [%s]", secretFile.getAbsolutePath()));
        }
    }
}
