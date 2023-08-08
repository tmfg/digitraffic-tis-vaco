package fi.digitraffic.tis.vaco.organization.service;

import fi.digitraffic.tis.vaco.organization.model.Cooperation;
import fi.digitraffic.tis.vaco.organization.model.CooperationType;
import fi.digitraffic.tis.vaco.organization.model.ImmutableCooperation;
import fi.digitraffic.tis.vaco.organization.model.Organization;
import fi.digitraffic.tis.vaco.organization.repository.CooperationRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CooperationService {

    private final CooperationRepository cooperationRepository;

    public CooperationService(CooperationRepository cooperationRepository) {
        this.cooperationRepository = cooperationRepository;
    }

    public Optional<Cooperation> create(CooperationType cooperationType, Organization partnerA, Organization partnerB) {
        if(cooperationRepository.findByIds(cooperationType, partnerA.id(), partnerB.id()).isPresent()) {
            return Optional.empty();
        }

        return Optional.of(cooperationRepository.create(
            ImmutableCooperation.of(
                cooperationType,
                partnerA,
                partnerB)));
    }
}
