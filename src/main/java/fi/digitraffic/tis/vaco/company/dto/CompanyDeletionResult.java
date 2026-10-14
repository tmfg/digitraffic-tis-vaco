package fi.digitraffic.tis.vaco.company.dto;

import java.util.Map;

/**
 * Outcome of an attempt to delete a company.
 *
 * @param status  what happened
 * @param deleted number of deleted linked rows by kind, non-empty only when {@code status} is {@link Status#DELETED}
 */
public record CompanyDeletionResult(Status status, Map<String, Long> deleted) {

    public enum Status {
        DELETED,
        NOT_FOUND,
        PROTECTED,
        IN_PROGRESS
    }

    public static CompanyDeletionResult of(Status status) {
        return new CompanyDeletionResult(status, Map.of());
    }

    public static CompanyDeletionResult deleted(Map<String, Long> deleted) {
        return new CompanyDeletionResult(Status.DELETED, deleted);
    }
}
