package no.skatteetaten.aurora.databasehotel.web.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.micrometer.core.annotation.Timed;

@RestController
public class DeprecatedEndpoints {

    private DatabaseInstanceController databaseInstanceController;

    private DatabaseSchemaController databaseSchemaController;

    public DeprecatedEndpoints(DatabaseInstanceController databaseInstanceController,
        DatabaseSchemaController databaseSchemaController) {
        this.databaseInstanceController = databaseInstanceController;
        this.databaseSchemaController = databaseSchemaController;
    }

    @GetMapping("/admin/databaseInstance/")
    @Timed
    public ResponseEntity<ApiResponse> databaseInstanceControllerFindAll() {

        return databaseInstanceController.findAll();
    }

    @PostMapping("/admin/databaseInstance/{host}/deleteUnused")
    @Timed
    public ResponseEntity<ApiResponse> databaseInstanceControllerDeleteUnused(@PathVariable String host) {

        return databaseInstanceController.deleteUnused(host);
    }

    @GetMapping("/schema/{id}")
    @Timed
    public ResponseEntity<ApiResponse> findById(@PathVariable String id) {

        return databaseSchemaController.findById(id);
    }

    @DeleteMapping("/schema/{id}")
    @Timed
    public ResponseEntity<ApiResponse> deleteById(@PathVariable String id) {

        return databaseSchemaController.deleteById(id);
    }

    @GetMapping("/schema/")
    @Timed
    public ResponseEntity<ApiResponse> findAll(@RequestParam(name = "labels", required = false) String labelsParam) {

        return databaseSchemaController.findAll(labelsParam);
    }

    @PostMapping("/schema/")
    @Timed
    public ResponseEntity<ApiResponse> create(@RequestBody SchemaCreationRequest schemaCreationRequest) {

        return databaseSchemaController.create(schemaCreationRequest);
    }
}
