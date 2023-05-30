package fi.digitraffic.tis.vaco;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

/**
 * Generic didn't-fit-anywhere-else configuration container
 */
@Configuration
public class VacoConfiguration {
    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder().build();
    }
}
