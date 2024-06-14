package fi.digitraffic.tis.vaco.badges;

import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.db.repositories.BadgeRepository;
import fi.digitraffic.tis.vaco.entries.EntryRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class BadgeService {

    private final BadgeRepository badgeRepository;

    private final CachingService cachingService;

    private final EntryRepository entryRepository;

    public BadgeService(BadgeRepository badgeRepository,
                        CachingService cachingService,
                        EntryRepository entryRepository) {
        this.badgeRepository = Objects.requireNonNull(badgeRepository);
        this.cachingService = Objects.requireNonNull(cachingService);
        this.entryRepository = Objects.requireNonNull(entryRepository);
    }

    public Optional<ClassPathResource> getBadge(String publicId) {
        return cachingService.cacheStatus(publicId, k -> badgeRepository.findEntryStatus(publicId).orElse(null))
            .flatMap(s ->
                cachingService.cacheClassPathResource("badges/" + s.fieldName() + ".svg", ClassPathResource::new));
    }

    public Optional<ClassPathResource> getBadge(String publicId, String taskName) {
        return cachingService.cacheStatus(publicId + "/" + taskName, k ->
                entryRepository.findByPublicId(publicId)
                    .flatMap(entry -> badgeRepository.findTaskStatus(entry, taskName))
                    .orElse(null))
            .flatMap(s ->
                cachingService.cacheClassPathResource("badges/" + s.fieldName() + ".svg", ClassPathResource::new));
    }

}
