package fi.digitraffic.tis.vaco.me;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.aaa.AuthorizationService;
import fi.digitraffic.tis.vaco.admintasks.AdminTasksService;
import fi.digitraffic.tis.vaco.admintasks.model.ImmutableGroupIdMappingTask;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.service.CompanyService;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class MeService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CompanyService companyService;

    private final AdminTasksService adminTasksService;

    private final AuthorizationService authorizationService;

    public MeService(CompanyService companyService,
                     AdminTasksService adminTasksService,
                     AuthorizationService authorizationService) {
        this.companyService = Objects.requireNonNull(companyService);
        this.adminTasksService = Objects.requireNonNull(adminTasksService);
        this.authorizationService = Objects.requireNonNull(authorizationService);
    }

    /**
     * Resolve group GUIDs from token to actual {@link Company Companies} and automagically register an admin task for
     * mapping the unidentified groups.
     *
     * @return set of company metadata the current user has access to
     */
    public Set<Company> findCompanies() {
        return authorizationService.currentToken().map(token -> {
            Set<String> allGroupIds = safeSet(token.getToken().getClaim("groups"));
            Set<Company> companies = companyService.findAllByAdGroupIds(List.copyOf(allGroupIds));

            Set<String> mappedGroupIds = Streams.map(companies, Company::adGroupId).toSet();
            Set<String> unmappedGroupIds = new HashSet<>(allGroupIds);
            unmappedGroupIds.removeAll(mappedGroupIds);
            unmappedGroupIds.forEach(u -> adminTasksService.registerGroupIdMappingTask(ImmutableGroupIdMappingTask.of(u)));
            return companies;
        }).orElseThrow(() -> new UnauthenticatedException("User not authorized in this context"));
    }

    /**
     * Print this string in WARN level logging every time user tries to access anything they do not have access to.
     * @return String with user details to track who's trying to access things they shouldn't.
     */
    public String alertText() {
        return authorizationService.currentToken()
            .map(token -> token.getToken().getClaimAsString("oid"))
            .orElse("unauthenticated");
    }

    public boolean isAllowedToAccess(Entry entry) {
        if (isAllowedToAccess(entry.businessId())) {
            return true;
        } else {
            logger.warn("User [{}] tried to access entry {} belonging to {}", alertText(), entry.publicId(), entry.businessId());
            return false;
        }
    }

    public boolean isAllowedToAccess(String businessId) {
        return authorizationService.currentToken()
            .flatMap(token -> Streams.filter(findCompanies(), c -> Objects.equals(c.businessId(), businessId))
                .findFirst())
            .isPresent();
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
