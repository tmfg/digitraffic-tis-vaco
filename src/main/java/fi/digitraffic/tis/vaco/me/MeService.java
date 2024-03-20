package fi.digitraffic.tis.vaco.me;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.admintasks.AdminTasksService;
import fi.digitraffic.tis.vaco.admintasks.model.ImmutableGroupIdMappingTask;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class MeService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final AdminTasksService adminTasksService;
    private final CompanyHierarchyService companyHierarchyService;

    public MeService(AdminTasksService adminTasksService,
                     CompanyHierarchyService companyHierarchyService) {
        this.adminTasksService = Objects.requireNonNull(adminTasksService);
        this.companyHierarchyService = Objects.requireNonNull(companyHierarchyService);
    }

    /**
     * Resolve group GUIDs from token to actual {@link Company Companies} and automagically register an admin task for
     * mapping the unidentified groups.
     *
     * @return set of company metadata the current user has access to
     */
    public Set<Company> findCompanies() {
        return currentToken().map(token -> {
            Set<String> allGroupIds = new HashSet<>(token.getToken().getClaimAsStringList("groups"));
            Set<Company> companies = companyHierarchyService.findAllByAdGroupIds(List.copyOf(allGroupIds));

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
        return currentToken()
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

    public boolean isAllowedToAccess(Company company) {
        if (isAllowedToAccess(company.businessId())) {
            return true;
        } else {
            logger.warn("User [{}] tried to access company {} without having rights for that", alertText(), company.businessId());
            return false;
        }
    }

    public boolean isAllowedToAccess(String businessId) {
        Set<Company> directMemberCompanies = findCompanies();

        return isAdmin()
            || hasDirectAccess(directMemberCompanies, businessId)
            || companyHierarchyService.isChildOfAny(directMemberCompanies, businessId);
    }

    public boolean isAdmin() {
        return currentToken().map(t -> {
            List<String> roles = t.getToken().getClaimAsStringList("roles");
            return roles != null && Set.copyOf(roles).contains("vaco.admin");
        }).orElse(false);
    }

    private static boolean hasDirectAccess(Set<Company> directMemberCompanies, String businessId) {
        for (Company c : directMemberCompanies) {
            if (c.businessId().equals(businessId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Use this method to gain access to current user's JWT token. Remember to handle missing tokens in case the user
     * hasn't authenticated!
     * <p>
     * There are other ways of accessing the token, e.g. through controller handler parameter injection, but doing so
     * blocks link generation. Also considering the current state of Spring Security's on-going migration/refactoring
     * and the amount of legacy documentation online it is really hard to quantify what is the least insane approach for
     * accessing the token itself.
     * <p>
     * For the reasons above, this service exists mainly to isolate the insanity, not for providing true authorization
     * related actions. Lean on Spring Security where available.
     *
     * @return Current user's {@link JwtAuthenticationToken}
     */
    public Optional<JwtAuthenticationToken> currentToken() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
            .filter(JwtAuthenticationToken.class::isInstance)
            .map(JwtAuthenticationToken.class::cast);
    }
}
