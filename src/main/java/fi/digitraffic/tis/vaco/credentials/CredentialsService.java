package fi.digitraffic.tis.vaco.credentials;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.api.model.credentials.CreateCredentialsRequest;
import fi.digitraffic.tis.vaco.api.model.credentials.UpdateCredentialsRequest;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import fi.digitraffic.tis.vaco.credentials.model.Credentials;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.CredentialsRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.queuehandler.mapper.EntryRequestMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class CredentialsService {
    private final CompanyHierarchyService companyHierarchyService;
    private final CompanyRepository companyRepository;
    private final EntryRequestMapper entryRequestMapper;
    private final CredentialsRepository credentialsRepository;
    private final RecordMapper recordMapper;

    public CredentialsService(EntryRequestMapper entryRequestMapper,
                              CompanyHierarchyService companyHierarchyService,
                              CredentialsRepository credentialsRepository,
                              CompanyRepository companyRepository, RecordMapper recordMapper) {
        this.entryRequestMapper = Objects.requireNonNull(entryRequestMapper);
        this.companyHierarchyService = Objects.requireNonNull(companyHierarchyService);
        this.credentialsRepository = Objects.requireNonNull(credentialsRepository);
        this.companyRepository = Objects.requireNonNull(companyRepository);
        this.recordMapper = recordMapper;
    }

    public Optional<Credentials> createCredentials(CreateCredentialsRequest credentials) {
        return companyRepository.findByBusinessId(credentials.owner())
            .flatMap(owner -> {
                Credentials converted = entryRequestMapper.toCredentials(credentials, companyHierarchyService::findByBusinessId);
                return credentialsRepository.createCredentials(converted, owner)
                    .map(cr -> recordMapper.toCredentials(cr, owner));
            });
    }

    public Optional<List<Credentials>> findAllForBusinessId(String businessId) {
        return companyRepository.findByBusinessId(businessId)
            .map(owner -> Streams.collect(
                credentialsRepository.findAllForCompany(owner),
                cr -> recordMapper.toCredentials(cr, owner)));
    }

    public Optional<Credentials> findByPublicId(String publicId) {
        return credentialsRepository.findByPublicId(publicId)
            .map(this::asCredentialsEntity);
    }

    public Optional<Credentials> updateCredentials(String publicId, UpdateCredentialsRequest updates) {
        return credentialsRepository.findByPublicId(publicId)
            .map(previous -> credentialsRepository.updateCredentials(previous, updates.type(), updates.name(), updates.description(), updates.details()))
            .map(this::asCredentialsEntity);
    }

    private Credentials asCredentialsEntity(CredentialsRecord credentials) {
        return recordMapper.toCredentials(credentials, companyRepository.findById(credentials.ownerId()));
    }

    public Optional<Boolean> deleteCredentials(String publicId) {
        return credentialsRepository.deleteCredentials(publicId);
    }
}
