package fi.digitraffic.tis.vaco.credentials.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(name = CredentialsType.Name.HTTP_BASIC, value = HttpBasicAuthenticationDetails.class)
})
public interface AuthenticationDetails {
}
