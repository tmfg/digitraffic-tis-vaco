package fi.digitraffic.tis.vaco.credentials.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;

@JsonSubTypes({
    @JsonSubTypes.Type(name = CredentialsType.Name.HTTP_BASIC, value = HttpBasicAuthenticationDetails.class)
})
public interface AuthenticationDetails {
}
