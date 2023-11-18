package fi.digitraffic.tis.vaco.company.service;

import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.Cooperation;
import fi.digitraffic.tis.vaco.company.model.CooperationType;
import fi.digitraffic.tis.vaco.company.model.ImmutableCooperation;
import fi.digitraffic.tis.vaco.company.repository.CooperationRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CooperationService {

    private final CooperationRepository cooperationRepository;

    public CooperationService(CooperationRepository cooperationRepository) {
        this.cooperationRepository = cooperationRepository;
    }

    public Optional<Cooperation> create(CooperationType cooperationType, Company partnerA, Company partnerB) {
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
