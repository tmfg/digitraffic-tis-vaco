package fi.digitraffic.tis.vaco.admintasks;

import fi.digitraffic.tis.vaco.admintasks.model.GroupIdMappingTask;
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

    public AdminTasksService(AdminTaskRepository adminTaskRepository) {
        this.adminTaskRepository = Objects.requireNonNull(adminTaskRepository);
    }

    public GroupIdMappingTask registerGroupIdMappingTask(GroupIdMappingTask groupIdMappingTask) {
        GroupIdMappingTask result = adminTaskRepository.create(groupIdMappingTask);
        logger.debug("Registered admin task for mapping {} as {}", result.groupId(), result.publicId());
        return result;
    }

    public Pair<GroupIdMappingTask, Company> resolveAsPairedToCompany(GroupIdMappingTask task, Company company) {
        logger.debug("Resolve {} as matching {} to company {}", task.publicId(), task.groupId(), company.businessId());
        return adminTaskRepository.resolve(task, company);
    }

    public GroupIdMappingTask resolveAsSkippable(GroupIdMappingTask task) {
        logger.debug("Resolve {} as skipped", task.publicId());
        return adminTaskRepository.resolveSkipped(task);
    }

    public List<GroupIdMappingTask> listUnresolvedGroupIdMappingTasks() {
        return adminTaskRepository.findAllOpenGroupIdTasks();
    }

    public boolean removeGroupIdMappingTask(GroupIdMappingTask task) {
        return adminTaskRepository.deleteGroupIdMappingTask(task);
    }

    public Optional<GroupIdMappingTask> findGroupIdMappingTaskByPublicId(String publicId) {
        return adminTaskRepository.findGroupIdTaskByPublicId(publicId);
    }
}
