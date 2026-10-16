package fi.digitraffic.tis.vaco.company.dto;

import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for the admin endpoint that adds a company.
 *
 * @param businessId Finnish business ID of the new company
 * @param name       name of the new company
 * @param publish    value of the {@code publish} flag of the new company
 */
public record CreateCompanyRequest(@NotNull @BusinessId String businessId,
                                   @NotBlank String name,
                                   boolean publish) {

    public Company toCompany() {
        return ImmutableCompany.of(businessId, name, publish);
    }
}
