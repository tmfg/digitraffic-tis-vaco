package fi.digitraffic.tis.vaco.api.model;

import jakarta.annotation.Nullable;

import java.util.Map;

public record Resource<D>(
    @Nullable
    D data,

    @Nullable
    String error,

    @Nullable
    Map<String, Map<String, Link>> links) {


    public static <D> Resource<D> resource(D data) {
        return resource(data, null);
    }
    public static <D> Resource<D> resource(D data, String error) {
        return new Resource<>(
            data,
            error,
            Map.of());
    }
}
