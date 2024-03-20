package fi.digitraffic.tis.vaco.badges;

import fi.digitraffic.tis.vaco.caching.CachingService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class BadgeService {

    private final BadgeRepository badgeRepository;

    private final CachingService cachingService;

    public BadgeService(BadgeRepository badgeRepository, CachingService cachingService) {
        this.badgeRepository = Objects.requireNonNull(badgeRepository);
        this.cachingService = Objects.requireNonNull(cachingService);
    }

    public Optional<ClassPathResource> getBadge(String publicId) {
        return cachingService.cacheStatus(publicId, k -> badgeRepository.findEntryStatus(publicId).orElse(null))
            .flatMap(s ->
                cachingService.cacheClassPathResource("badges/" + s.fieldName() + ".svg", ClassPathResource::new));
    }

    public Optional<ClassPathResource> getBadge(String publicId, String taskName) {
        return cachingService.cacheStatus(publicId, k -> badgeRepository.findTaskStatus(publicId, taskName).orElse(null))
            .flatMap(s ->
                cachingService.cacheClassPathResource("badges/" + s.fieldName() + ".svg", ClassPathResource::new));
    }

}
