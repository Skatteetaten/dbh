package no.skatteetaten.aurora.databasehotel.utils;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CollectionUtils {

    public static <F, T> List<T> mapToList(Collection<F> objects, Function<F, T> mapper) {

        if (objects == null) {
            throw new IllegalArgumentException("Cannot create list from null");
        }
        return objects.stream().map(mapper).collect(Collectors.<T>toList());
    }
}
