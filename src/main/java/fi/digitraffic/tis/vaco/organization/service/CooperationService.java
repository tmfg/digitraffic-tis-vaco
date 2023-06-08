package fi.digitraffic.tis.vaco.organization.service;

import fi.digitraffic.tis.vaco.InvalidInputException;
import fi.digitraffic.tis.vaco.ItemExistsException;
import fi.digitraffic.tis.vaco.organization.model.ImmutableCooperation;
import fi.digitraffic.tis.vaco.organization.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.organization.repository.CooperationRepository;
import fi.digitraffic.tis.vaco.organization.repository.OrganizationRepository;
import fi.digitraffic.tis.vaco.validation.dto.ImmutableCooperationCommand;
import org.springframework.stereotype.Service;

@Service
public class CooperationService {

    private final OrganizationRepository organizationRepository;
    private final CooperationRepository cooperationRepository;

    public CooperationService(OrganizationRepository organizationRepository, CooperationRepository cooperationRepository) {
        this.organizationRepository = organizationRepository;
        this.cooperationRepository = cooperationRepository;
    }

    public ImmutableCooperationCommand create(ImmutableCooperationCommand cooperationDto) {
        if(cooperationDto.partnerABusinessId().equals(cooperationDto.partnerBBusinessId())) {
            // This perhaps could have been validation on ImmutableCooperationDto validation annotations level
            // but not clear how yet
            // TODO: error messages need to move into constants and maybe be stored somewhere special
            throw new InvalidInputException("Cooperation partners cannot be the same");
        }
        ImmutableOrganization partnerA = organizationRepository.getByBusinessId(cooperationDto.partnerABusinessId());
        ImmutableOrganization partnerB = organizationRepository.getByBusinessId(cooperationDto.partnerBBusinessId());
        if(cooperationRepository.findByIds(cooperationDto.cooperationType(),
            partnerA.id(), partnerB.id()).isPresent()) {
            throw new ItemExistsException("A cooperation between given business ID-s already exists");
        }

        ImmutableCooperation cooperation = ImmutableCooperation.builder()
            .cooperationType(cooperationDto.cooperationType())
            .partnerA(partnerA.id())
            .partnerB(partnerB.id())
            .build();
        cooperationRepository.create(cooperation);
        return cooperationDto;
    }
}
