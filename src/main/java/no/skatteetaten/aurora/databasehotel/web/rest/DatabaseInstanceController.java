package no.skatteetaten.aurora.databasehotel.web.rest;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import static no.skatteetaten.aurora.databasehotel.utils.MapUtils.kv;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.micrometer.core.annotation.Timed;
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo;
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelAdminService;
import no.skatteetaten.aurora.databasehotel.service.DatabaseInstance;
import no.skatteetaten.aurora.databasehotel.utils.MapUtils;

@RestController
@RequestMapping("/api/v1/admin/databaseInstance")
public class DatabaseInstanceController {

    private final DatabaseHotelAdminService databaseHotelAdminService;

    @Autowired
    public DatabaseInstanceController(DatabaseHotelAdminService databaseHotelAdminService) {
        this.databaseHotelAdminService = databaseHotelAdminService;
    }

    public static Map<String, Object> toResource(DatabaseInstance databaseInstance) {
        DatabaseInstanceMetaInfo metaInfo = databaseInstance.getMetaInfo();
        return MapUtils.from(
            kv("host", metaInfo.getHost()),
            kv("instanceName", metaInfo.getInstanceName()),
            kv("port", metaInfo.getPort())
        );
    }

    @RequestMapping(value = "/", method = GET)
    @Timed
    public ResponseEntity<ApiResponse> findAll() {

        Set<DatabaseInstance> databaseInstances = databaseHotelAdminService.findAllDatabaseInstances();
        List<Map<String, Object>> resources =
            databaseInstances.stream().map(DatabaseInstanceController::toResource).collect(Collectors.toList());

        return Responses.okResponse(resources);
    }

    @RequestMapping(value = "/{host}/deleteUnused", method = POST)
    @Timed
    public ResponseEntity<ApiResponse> deleteUnused(@PathVariable String host) {

        databaseHotelAdminService.findDatabaseInstanceByHost(host)
            .ifPresent(DatabaseInstance::deleteUnusedSchemas);

        return Responses.okResponse();
    }
}
