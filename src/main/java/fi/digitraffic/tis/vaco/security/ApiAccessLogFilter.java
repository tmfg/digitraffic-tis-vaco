package fi.digitraffic.tis.vaco.security;

import com.nimbusds.jwt.JWTParser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Writes one INFO line for each request that an application token authenticates and for each request that ends with
 * 401. A request with a user token that does not end with 401 writes no line.
 * <p>
 * The line is {@code method path status duration issuer client ver}. The path is the raw request path without the
 * query string. The line never contains a claim of a user token, and never contains a value from a token that failed
 * validation.
 * <p>
 * This class is deliberately not a Spring bean. Spring Boot registers every {@code Filter} bean a second time as a
 * servlet filter. The security configuration creates the instance and adds it to the security filter chain.
 */
public class ApiAccessLogFilter extends OncePerRequestFilter {

    static final String LOGGER_NAME = "fi.digitraffic.tis.vaco.security.ApiAccess";

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger(LOGGER_NAME);
    private static final String NONE = "-";
    private static final String ISSUER_NONE = "none";
    private static final String ISSUER_ENTRA = "entra";

    private final String entraIssuer;
    private final String entraV1Issuer;
    private final String trivoreIssuer;
    private final DefaultBearerTokenResolver tokenResolver = new DefaultBearerTokenResolver();

    /**
     * @param entraIssuer Issuer of the Entra ID v2.0 tokens
     * @param entraTenantId Tenant of the Entra ID tokens, used to recognize the v1.0 issuer
     * @param trivoreIssuer Trivore issuer of the profile, or null when the Trivore path is off
     */
    public ApiAccessLogFilter(String entraIssuer, String entraTenantId, String trivoreIssuer) {
        this.entraIssuer = entraIssuer;
        this.entraV1Issuer = "https://sts.windows.net/" + entraTenantId + "/";
        this.trivoreIssuer = trivoreIssuer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long started = System.nanoTime();
        boolean failed = true;
        try {
            filterChain.doFilter(request, response);
            failed = false;
        } finally {
            // An exception that leaves the chain has no response status yet, and the container answers 500.
            int status = failed ? HttpStatus.INTERNAL_SERVER_ERROR.value() : response.getStatus();
            write(request, status, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
    }

    private void write(HttpServletRequest request, int status, long millis) {
        if (!ACCESS_LOG.isInfoEnabled()) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String issuer;
        String client = NONE;
        String version = NONE;
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication && jwtAuthentication.isAuthenticated()) {
            Jwt jwt = jwtAuthentication.getToken();
            if (trivoreIssuer != null && trivoreIssuer.equals(jwt.getIssuer().toString())) {
                issuer = "trivore";
                client = jwt.getSubject();
            } else {
                issuer = ISSUER_ENTRA;
                if (jwt.hasClaim("scp")) {
                    // user token: only a 401 is logged, and no claim of the user is read
                    if (status != HttpStatus.UNAUTHORIZED.value()) {
                        return;
                    }
                } else {
                    client = firstPresent(
                        jwt.getClaimAsString("azp"), firstPresent(jwt.getClaimAsString("appid"), NONE));
                    version = firstPresent(jwt.getClaimAsString("ver"), NONE);
                }
            }
        } else if (status == HttpStatus.UNAUTHORIZED.value()) {
            issuer = unverifiedIssuerLabel(request);
        } else {
            return;
        }
        ACCESS_LOG.info("{} {} {} {}ms issuer={} client={} ver={}",
            clean(request.getMethod()), clean(request.getRequestURI()), status, millis,
            issuer, clean(client), clean(version));
    }

    /**
     * Replaces each ISO control character with an underscore, so that one request writes one line.
     */
    private static String clean(String value) {
        if (value == null) {
            return NONE;
        }
        StringBuilder out = new StringBuilder(value.length());
        value.codePoints().forEach(cp -> out.appendCodePoint(Character.isISOControl(cp) ? '_' : cp));
        return out.toString();
    }

    private static String firstPresent(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }

    /**
     * Chooses a label from a fixed set. The method compares the unverified issuer and never writes it.
     */
    private String unverifiedIssuerLabel(HttpServletRequest request) {
        try {
            String token = tokenResolver.resolve(request);
            if (token == null) {
                return ISSUER_NONE;
            }
            String issuer = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
            if (issuer == null) {
                return ISSUER_NONE;
            }
            if (issuer.equals(entraIssuer)) {
                return ISSUER_ENTRA;
            }
            if (issuer.equals(entraV1Issuer)) {
                return "entra-v1";
            }
            if (issuer.equals(trivoreIssuer)) {
                return "trivore";
            }
            return "unknown";
        } catch (Exception _) {
            // the line must never change the response, whatever the token looks like
            return ISSUER_NONE;
        }
    }
}
