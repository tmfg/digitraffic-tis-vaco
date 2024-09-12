package fi.digitraffic.tis.vaco.me;

import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import fi.digitraffic.tis.vaco.fintrafficid.FintrafficIdService;
import fi.digitraffic.tis.vaco.fintrafficid.model.FintrafficIdGroup;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.jetbrains.annotations.NotNull;
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

    private final FintrafficIdService fintrafficIdService;

    private final CompanyHierarchyService companyHierarchyService;

    public MeService(CompanyHierarchyService companyHierarchyService,
                     FintrafficIdService fintrafficIdService) {
        this.companyHierarchyService = Objects.requireNonNull(companyHierarchyService);
        this.fintrafficIdService = Objects.requireNonNull(fintrafficIdService);
    }

    public Set<Company> findCompanies() {
        return currentToken().map(token -> token.getToken().getClaimAsString("oid"))
            .map(this::findCompanies)
            .orElse(Set.of());
    }

    /**
     * Resolve user's {@link Company Companies} access from MS Graph.
     *
     * @return set of company metadata the current user has access to
     */
    public Set<Company> findCompanies(String userId) {
        return currentToken().map(token -> {
            Set<Company> companies = new HashSet<>();
            List<FintrafficIdGroup> groups = fintrafficIdService.getGroups(userId);

            groups.forEach(g -> g.organizationData().ifPresent(od -> {
                String groupId = g.id();
                String businessId = od.businessId();

                companyHierarchyService.findByAdGroupId(groupId)
                    .or(() -> updateKnownCompany(businessId, groupId))
                    .ifPresent(companies::add);
            }));

            if (companies.isEmpty()) {
                return Set.of(companyHierarchyService.getPublicTestCompany());
            }
            return companies;
        }).orElseThrow(() -> new UnauthenticatedException("User not authorized in this context"));
    }

    private @NotNull Optional<Company> updateKnownCompany(String businessId, String groupId) {
        return companyHierarchyService
            .findByBusinessId(businessId)
            .map(mc -> companyHierarchyService.updateAdGroupId(mc, groupId));
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
