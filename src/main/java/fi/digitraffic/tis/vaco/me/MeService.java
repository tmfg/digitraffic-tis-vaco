package fi.digitraffic.tis.vaco.me;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.admintasks.AdminTasksService;
import fi.digitraffic.tis.vaco.admintasks.model.ImmutableGroupIdMappingTask;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.service.CompanyService;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class MeService {

    private final CompanyService companyService;

    private final AdminTasksService adminTasksService;

    public MeService(CompanyService companyService, AdminTasksService adminTasksService) {
        this.companyService = Objects.requireNonNull(companyService);
        this.adminTasksService = Objects.requireNonNull(adminTasksService);
    }

    /**
     * Resolve group GUIDs from token to actual {@link Company Companies} and automagically register an admin task for
     * mapping the unidentified groups.
     *
     * @param token Spring JWT token
     * @return set of company metadata the user which the token represents has access to
     */
    public Set<Company> findCompanies(JwtAuthenticationToken token) {
        Set<String> allGroupIds = safeSet(token.getToken().getClaim("groups"));
        Set<Company> companies = companyService.findAllByAdGroupIds(List.copyOf(allGroupIds));

        Set<String> mappedGroupIds = Streams.map(companies, Company::adGroupId).toSet();
        Set<String> unmappedGroupIds = new HashSet<>(allGroupIds);
        unmappedGroupIds.removeAll(mappedGroupIds);
        unmappedGroupIds.forEach(u -> adminTasksService.registerGroupIdMappingTask(ImmutableGroupIdMappingTask.of(u)));
        return companies;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> safeSet(Object maybeColl) {
        if (maybeColl != null
            && Collection.class.isAssignableFrom(maybeColl.getClass())) {
            return new HashSet<>((Collection<String>) maybeColl);
        } else {
            return Set.of();
        }

    }

}
