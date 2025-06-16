package fi.digitraffic.tis.vaco;

import fi.digitraffic.http.HttpClient;
import fi.digitraffic.http.HttpClientConfiguration;
import fi.digitraffic.http.ImmutableHttpClientConfiguration;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.configuration.JaxbHttpMessageConverter;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.credentials.CredentialsService;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.featureflags.FeatureFlagsService;
import fi.digitraffic.tis.vaco.fintrafficid.FintrafficIdService;
import fi.digitraffic.tis.vaco.fintrafficid.MsGraphBackedFintrafficIdService;
import fi.digitraffic.tis.vaco.http.VacoHttpClient;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.reactive.function.client.WebClient;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generic didn't-fit-anywhere-else configuration container
 */
@Configuration
@PropertySource(value = "classpath:git.properties", ignoreResourceNotFound = true)
public class VacoConfiguration {

    @Bean
    public VacoHttpClient httpClient(VacoProperties vacoProperties,
                                     @Value("${git.commit.id.abbrev:#{null}}")
                                     String gitCommitIdAbbreviation,
                                     CredentialsService credentialsService,
                                     EntryService entryService,
                                     FeatureFlagsService featureFlagsService) {
        HttpClientConfiguration configuration = ImmutableHttpClientConfiguration.builder()
            .baseUri(vacoProperties.baseUrl())
            .userAgentExtension("VACO/" + ((gitCommitIdAbbreviation != null) ? gitCommitIdAbbreviation : vacoProperties.environment()))
            .build();
        return new VacoHttpClient(new HttpClient(configuration), credentialsService, entryService, featureFlagsService);
    }

    @Bean
    public S3Client s3ClientUtility(VacoProperties vacoProperties,
                                    software.amazon.awssdk.services.s3.S3Client awsS3Client,
                                    S3TransferManager s3TransferManager) {
        return new S3Client(vacoProperties, s3TransferManager,awsS3Client);
    }

    @ConditionalOnProperty(name = "vaco.ms-graph.client-secret")
    @Bean
    public FintrafficIdService fintrafficIdService(VacoProperties vacoProperties, CachingService cachingService) {
        return new MsGraphBackedFintrafficIdService(vacoProperties, cachingService);
    }

    @Bean
    public HttpMessageConverter<Object> netexHttpMessageConverter() {
        return new JaxbHttpMessageConverter(PublicationDeliveryStructure.class);
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public WebClient webClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
            .defaultHeader("User-Agent", "Fintraffic TIS-VACO/" + LocalDate.now().format(DateTimeFormatter.ISO_DATE))
            .build();
    }

    @Bean
    public OpenAPI openAPIConfiguration() {
        return new OpenAPI()
            .components(new Components()
                .addSecuritySchemes("Bearer",
                    new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
            )
            .addSecurityItem(new SecurityRequirement().addList("Bearer"));
    }
}
