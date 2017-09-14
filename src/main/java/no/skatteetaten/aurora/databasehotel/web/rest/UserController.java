package no.skatteetaten.aurora.databasehotel.web.rest;

import java.util.Map;

import no.skatteetaten.aurora.databasehotel.domain.User;
import no.skatteetaten.aurora.databasehotel.utils.MapUtils;

public class UserController {

    public static Map<String, Object> toResource(User user) {

        return MapUtils.from(
            MapUtils.kv("username", user.getName()),
            MapUtils.kv("password", user.getPassword()),
            MapUtils.kv("type", user.getType())
        );
    }

}
