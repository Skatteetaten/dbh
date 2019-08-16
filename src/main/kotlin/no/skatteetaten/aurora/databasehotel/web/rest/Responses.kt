package no.skatteetaten.aurora.databasehotel.web.rest;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class Responses {

    public static ResponseEntity<ApiResponse> okResponse() {

        return okResponse(new ArrayList<>());
    }

    public static ResponseEntity<ApiResponse> okResponse(Object resource) {

        List<Object> resources = new ArrayList<>();
        resources.add(resource);
        return okResponse(resources);
    }

    public static ResponseEntity<ApiResponse> okResponse(List<?> resources) {

        return new ResponseEntity<>(new ApiResponse<>(resources), HttpStatus.OK);
    }
}
