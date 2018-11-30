package no.skatteetaten.aurora.databasehotel.web.rest;

import static java.lang.String.format;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import static no.skatteetaten.aurora.databasehotel.utils.CollectionUtils.mapToList;
import static no.skatteetaten.aurora.databasehotel.utils.MapUtils.kv;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.base.Strings;

import io.micrometer.core.annotation.Timed;
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema;
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchemaMetaData;
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelService;
import no.skatteetaten.aurora.databasehotel.utils.MapUtils;

@RestController
@RequestMapping("/api/v1/schema")
public class DatabaseSchemaController {

    private final boolean schemaListingAllowed;

    private final boolean dropAllowed;

    private final DatabaseHotelService databaseHotelService;

    @Autowired
    public DatabaseSchemaController(DatabaseHotelService databaseHotelService,
        @Value("${database-config.schemaListingAllowed}") boolean schemaListingAllowed,
        @Value("${database-config.dropAllowed}") boolean dropAllowed) {

        this.databaseHotelService = databaseHotelService;
        this.schemaListingAllowed = schemaListingAllowed;
        this.dropAllowed = dropAllowed;
    }

    public static Map<String, Object> toResource(DatabaseSchema schema) {

        return MapUtils.from(
            kv("id", schema.getId()),
            kv("type", schema.getType()),
            kv("jdbcUrl", schema.getJdbcUrl()),
            kv("name", schema.getName()),
            kv("createdDate", schema.getCreatedDate()),
            kv("lastUsedDate", schema.getLastUsedDate()),
            kv("databaseInstance", MapUtils.from(
                kv("host", schema.getDatabaseInstanceMetaInfo().getHost()),
                kv("port", schema.getDatabaseInstanceMetaInfo().getPort())
            )),
            kv("users", mapToList(schema.getUsers(), UserController::toResource)),
            kv("labels", schema.getLabels()),
            kv("metadata", new HashMap<String, Object>() {
                {
                    put("sizeInMb", schema.getMetaData().map(DatabaseSchemaMetaData::getSizeInMb).orElse(null));
                }
            })
        );
    }

    static Map<String, String> parseLabelsParam(@RequestParam(required = false) String labelsParam) {

        String labelsDecoded;
        try {
            labelsDecoded = URLDecoder.decode(Strings.nullToEmpty(labelsParam), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }

        Map<String, String> labels = new HashMap<>();
        if (labelsDecoded.trim().length() == 0) {
            return labels;
        }

        String[] labelsUnparsed = labelsDecoded.split(",");
        for (String unparsedLabel : labelsUnparsed) {
            String[] nameAndValue = unparsedLabel.split("=");
            String name = nameAndValue[0];
            String value = null;
            if (nameAndValue.length == 2) {
                value = nameAndValue[1];
            }
            labels.put(name, value);
        }
        return labels;
    }

    @GetMapping("/{id}")
    @Timed
    public ResponseEntity<ApiResponse> findById(@PathVariable String id) {

        DatabaseSchema databaseSchema = databaseHotelService.findSchemaById(id)
            .map(Pair::getLeft)
            .orElseThrow(() -> new IllegalArgumentException(format("No such schema %s", id)));
        return Responses.okResponse(toResource(databaseSchema));
    }

    @DeleteMapping(value = "/{id}")
    @Timed
    public ResponseEntity<ApiResponse> deleteById(@PathVariable String id,
        @RequestHeader(name = "cooldown-duration-hours", required = false) Integer cooldownDurationHours) {

        if (!dropAllowed) {
            throw new OperationDisabledException("Schema deletion has been disabled for this instance");
        }

        Duration cooldownDuration = cooldownDurationHours != null ? Duration.ofHours(cooldownDurationHours) : null;
        databaseHotelService.deleteSchemaById(id, cooldownDuration);
        return Responses.okResponse();
    }

    @GetMapping("/")
    @Timed
    public ResponseEntity<ApiResponse> findAll(
        @RequestParam(name = "labels", required = false) String labelsParam,
        @RequestParam(name = "q", required = false) String query
    ) {

        if (!schemaListingAllowed) {
            throw new OperationDisabledException("Schema listing has been disabled for this instance");
        }
        Set<DatabaseSchema> schemas;
        if ("for-deletion".equals(query)) {
            schemas = databaseHotelService.findAllDatabaseSchemasForDeletion();
        } else if (Strings.isNullOrEmpty(labelsParam)) {
            schemas = databaseHotelService.findAllDatabaseSchemas();
        } else {
            Map<String, String> labels = parseLabelsParam(labelsParam);
            schemas = databaseHotelService.findAllDatabaseSchemasByLabels(labels);
        }
        List<DatabaseSchema> sortedSchemas = schemas.stream()
            .sorted(comparing(DatabaseSchema::getLastUsedOrCreatedDate)).collect(toList());

        List<Map<String, Object>> resources = mapToList(sortedSchemas, DatabaseSchemaController::toResource);
        return Responses.okResponse(resources);
    }

    @PutMapping("/{id}")
    @Timed
    public ResponseEntity<ApiResponse> update(@PathVariable String id,
        @RequestBody SchemaCreationRequest schemaCreationRequest) {

        Map<String, String> labels = schemaCreationRequest.getLabels();
        Map<String, String> schemaInfo = schemaCreationRequest.getSchema();
        DatabaseSchema databaseSchema;
        if (schemaInfo != null) {
            String username = schemaInfo.get("username");
            String jdbcUrl = schemaInfo.get("jdbcUrl");
            String password = schemaInfo.get("password");
            databaseSchema = databaseHotelService.updateSchema(id, labels, username, jdbcUrl, password);
        } else {
            databaseSchema = databaseHotelService.updateSchema(id, labels);
        }
        return Responses.okResponse(toResource(databaseSchema));
    }

    @PostMapping("/")
    @Timed
    public ResponseEntity<ApiResponse> create(@RequestBody SchemaCreationRequest schemaCreationRequest) {

        DatabaseSchema databaseSchema;
        Map<String, String> labels = schemaCreationRequest.getLabels();

        if (schemaCreationRequest.getSchema() == null) {
            databaseSchema = databaseHotelService.createSchema(schemaCreationRequest.getInstanceName(), labels);
        } else {
            Map<String, String> schema = schemaCreationRequest.getSchema();
            boolean containsRequiredKeys = schema != null && schema.containsKey("username") &&
                schema.containsKey("password") && schema.containsKey("jdbcUrl");
            if (!containsRequiredKeys) {
                throw new IllegalArgumentException("Missing required values for creating external schema registration");
            }
            Map<String, String> schemaInfo = schemaCreationRequest.getSchema();
            String username = schemaInfo.get("username");
            String password = schemaInfo.get("password");
            String jdbcUrl = schemaInfo.get("jdbcUrl");
            databaseSchema = databaseHotelService.registerExternalSchema(username, password, jdbcUrl, labels);
        }
        return Responses.okResponse(toResource(databaseSchema));
    }
}
