package fi.digitraffic.tis.vaco.organization.service;

import fi.digitraffic.tis.vaco.organization.dto.ImmutableCooperationRequest;
import fi.digitraffic.tis.vaco.organization.mapper.CooperationRequestMapper;
import fi.digitraffic.tis.vaco.organization.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.organization.repository.CooperationRepository;
import fi.digitraffic.tis.vaco.organization.repository.OrganizationRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CooperationService {

    private final OrganizationRepository organizationRepository;
    private final CooperationRepository cooperationRepository;

    private final CooperationRequestMapper cooperationRequestMapper;

    public CooperationService(OrganizationRepository organizationRepository, CooperationRepository cooperationRepository, CooperationRequestMapper cooperationRequestMapper) {
        this.organizationRepository = organizationRepository;
        this.cooperationRepository = cooperationRepository;
        this.cooperationRequestMapper = cooperationRequestMapper;
    }

    public Optional<ImmutableCooperationRequest> create(ImmutableCooperationRequest cooperationRequest) {
        ImmutableOrganization partnerA = organizationRepository.getByBusinessId(cooperationRequest.partnerABusinessId());
        ImmutableOrganization partnerB = organizationRepository.getByBusinessId(cooperationRequest.partnerBBusinessId());
        if(cooperationRepository.findByIds(cooperationRequest.cooperationType(),
            partnerA.id(), partnerB.id()).isPresent()) {
            return Optional.empty();
        }

        cooperationRepository.create(cooperationRequestMapper.toCooperation(
            cooperationRequest.cooperationType(),
            partnerA.id(),
            partnerB.id()));
        return Optional.of(cooperationRequest);
    }
}
