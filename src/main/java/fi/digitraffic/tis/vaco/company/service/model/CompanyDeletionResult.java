package fi.digitraffic.tis.vaco.company.service.model;

import java.util.Map;

/**
 * Outcome of an attempt to delete a company.
 *
 * @param status     what happened
 * @param references number of linked rows by kind, non-empty only when {@code status} is {@link Status#REFERENCED}
 */
public record CompanyDeletionResult(Status status, Map<String, Long> references) {

    public enum Status {
        DELETED,
        NOT_FOUND,
        PROTECTED,
        REFERENCED
    }

    public static CompanyDeletionResult of(Status status) {
        return new CompanyDeletionResult(status, Map.of());
    }

    public static CompanyDeletionResult referenced(Map<String, Long> references) {
        return new CompanyDeletionResult(Status.REFERENCED, references);
    }
}
