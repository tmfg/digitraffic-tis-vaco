package fi.digitraffic.tis.vaco;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Generic didn't-fit-anywhere-else configuration container
 */
@Configuration
public class VacoConfiguration {

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder().build();
    }

    @Bean(name = "rulesetNameCache")
    public Cache<String, Ruleset> rulesetNameCache() {
        Cache<String, Ruleset> cache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofHours(1))
            .build();
        return cache;
    }
}
