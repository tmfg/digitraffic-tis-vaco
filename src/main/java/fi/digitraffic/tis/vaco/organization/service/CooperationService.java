package fi.digitraffic.tis.vaco.organization.service;

import fi.digitraffic.tis.vaco.organization.dto.ImmutableCooperationCommand;
import fi.digitraffic.tis.vaco.organization.mapper.CooperationCommandMapper;
import fi.digitraffic.tis.vaco.organization.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.organization.repository.CooperationRepository;
import fi.digitraffic.tis.vaco.organization.repository.OrganizationRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CooperationService {

    private final OrganizationRepository organizationRepository;
    private final CooperationRepository cooperationRepository;

    private final CooperationCommandMapper cooperationCommandMapper;

    public CooperationService(OrganizationRepository organizationRepository, CooperationRepository cooperationRepository, CooperationCommandMapper cooperationCommandMapper) {
        this.organizationRepository = organizationRepository;
        this.cooperationRepository = cooperationRepository;
        this.cooperationCommandMapper = cooperationCommandMapper;
    }

    public Optional<ImmutableCooperationCommand> create(ImmutableCooperationCommand cooperationCommand) {
        ImmutableOrganization partnerA = organizationRepository.getByBusinessId(cooperationCommand.partnerABusinessId());
        ImmutableOrganization partnerB = organizationRepository.getByBusinessId(cooperationCommand.partnerBBusinessId());
        if(cooperationRepository.findByIds(cooperationCommand.cooperationType(),
            partnerA.id(), partnerB.id()).isPresent()) {
            return Optional.empty();
        }

        cooperationRepository.create(cooperationCommandMapper.toCooperation(
            cooperationCommand.cooperationType(),
            partnerA.id(),
            partnerB.id()));
        return Optional.of(cooperationCommand);
    }
}
