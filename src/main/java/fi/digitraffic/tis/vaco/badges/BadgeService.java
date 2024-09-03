package fi.digitraffic.tis.vaco.badges;

import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.db.repositories.TaskRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class BadgeService {

    private final CachingService cachingService;

    private final EntryRepository entryRepository;

    private final TaskRepository taskRepository;

    public BadgeService(CachingService cachingService,
                        EntryRepository entryRepository,
                        TaskRepository taskRepository) {
        this.cachingService = Objects.requireNonNull(cachingService);
        this.entryRepository = Objects.requireNonNull(entryRepository);
        this.taskRepository = Objects.requireNonNull(taskRepository);
    }

    public Optional<ClassPathResource> getBadge(String publicId) {
        return cachingService.cacheStatus(publicId, k -> entryRepository.findEntryStatus(publicId).orElse(null))
            .flatMap(s ->
                cachingService.cacheClassPathResource("badges/" + s.fieldName() + ".svg", ClassPathResource::new));
    }

    public Optional<ClassPathResource> getBadge(String publicId, String taskName) {
        return cachingService.cacheStatus(publicId + "/" + taskName, k ->
                entryRepository.findByPublicId(publicId)
                    .flatMap(entry -> taskRepository.findTaskStatus(entry, taskName))
                    .orElse(null))
            .flatMap(s ->
                cachingService.cacheClassPathResource("badges/" + s.fieldName() + ".svg", ClassPathResource::new));
    }

}
