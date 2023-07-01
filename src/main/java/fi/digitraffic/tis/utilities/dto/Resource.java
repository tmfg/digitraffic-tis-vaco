package fi.digitraffic.tis.utilities.dto;

import jakarta.annotation.Nullable;

import java.util.Map;

public record Resource<D>(
    D data,

    @Nullable
    Map<String, Link> links) {
}
