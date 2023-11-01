package fi.digitraffic.tis.utilities.dto;

import jakarta.annotation.Nullable;

import java.util.Map;

public record Resource<D>(
    @Nullable
    D data,

    @Nullable
    String error,

    @Nullable
    Map<String, Map<String, Link>> links) {
}
