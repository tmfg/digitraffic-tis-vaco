package fi.digitraffic.tis.vaco;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.function.Function;

public class JwtTokenValidator implements OAuth2TokenValidator<Jwt> {

    private final Function<Jwt, Boolean> validator;
    private final OAuth2Error errorOnFailure;

    public JwtTokenValidator(Function<Jwt, Boolean> validator, OAuth2Error errorOnFailure) {
        this.validator = validator;
        this.errorOnFailure = errorOnFailure;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (Boolean.TRUE.equals(validator.apply(token))) {
            return OAuth2TokenValidatorResult.success();
        } else {
            return OAuth2TokenValidatorResult.failure(errorOnFailure);
        }
    }
}
