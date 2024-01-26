package fi.digitraffic.tis.vaco.admintasks;

import fi.digitraffic.tis.vaco.admintasks.model.GroupIdMappingTask;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.company.model.Company;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class AdminTasksService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final AdminTaskRepository adminTaskRepository;
    private final CachingService cachingService;

    public AdminTasksService(AdminTaskRepository adminTaskRepository, CachingService cachingService) {
        this.adminTaskRepository = Objects.requireNonNull(adminTaskRepository);
        this.cachingService = Objects.requireNonNull(cachingService);
    }

    public GroupIdMappingTask registerGroupIdMappingTask(GroupIdMappingTask groupIdMappingTask) {
        return cachingService.cacheAdminTask(getCacheKey(groupIdMappingTask), key -> {
            GroupIdMappingTask result = adminTaskRepository.create(groupIdMappingTask);
            logger.debug("Registered admin task for mapping {} as {}", result.groupId(), result.publicId());
            return result;
        });
    }

    public Pair<GroupIdMappingTask, Company> resolveAsPairedToCompany(GroupIdMappingTask task, Company company) {
        logger.debug("Resolve {} as matching {} to company {}", task.publicId(), task.groupId(), company.businessId());
        cachingService.invalidateAdminTask(getCacheKey(task));
        return adminTaskRepository.resolve(task, company);
    }

    public GroupIdMappingTask resolveAsSkippable(GroupIdMappingTask task) {
        return cachingService.forceUpdateAdminTask(getCacheKey(task), key -> {
            logger.debug("Resolve {} as skipped", task.publicId());
            return adminTaskRepository.resolveSkipped(task);
        });
    }

    public List<GroupIdMappingTask> listUnresolvedGroupIdMappingTasks() {
        return adminTaskRepository.findAllOpenGroupIdTasks();
    }

    public boolean removeGroupIdMappingTask(GroupIdMappingTask task) {
        cachingService.invalidateAdminTask(getCacheKey(task));
        return adminTaskRepository.deleteGroupIdMappingTask(task);
    }

    public Optional<GroupIdMappingTask> findGroupIdMappingTaskByPublicId(String publicId) {
        Optional<GroupIdMappingTask> task = adminTaskRepository.findGroupIdTaskByPublicId(publicId);
        return task.map(t -> cachingService.cacheAdminTask(getCacheKey(t), k -> t));
    }

    private static String getCacheKey(GroupIdMappingTask task) {
        return "groupid-mapping-" + task.groupId();
    }
}
