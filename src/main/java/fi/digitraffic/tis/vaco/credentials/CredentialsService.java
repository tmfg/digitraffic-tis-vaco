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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CredentialsService {
    private final CompanyHierarchyService companyHierarchyService;
    private final CompanyRepository companyRepository;
    private final EntryRequestMapper entryRequestMapper;
    private final CredentialsRepository credentialsRepository;
    private final RecordMapper recordMapper;
    private final Logger logger = LoggerFactory.getLogger(getClass());

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
        return Optional.ofNullable(publicId)
            .map(credentialsRepository::findByPublicId).flatMap(credentialsRecord -> credentialsRecord
                .map(this::asCredentialsEntity));
    }

    public Optional<Credentials> updateCredentials(String publicId, UpdateCredentialsRequest updates) {
        return credentialsRepository.findByPublicId(publicId)
            .map(previous -> credentialsRepository.updateCredentials(previous, updates.type(), updates.name(), updates.description(), updates.details(), updates.urlPattern()))
            .map(this::asCredentialsEntity);
    }

    private Credentials asCredentialsEntity(CredentialsRecord credentials) {
        return recordMapper.toCredentials(credentials, companyRepository.findById(credentials.ownerId()));
    }

    public Optional<Boolean> deleteCredentials(String publicId) {
        return credentialsRepository.deleteCredentials(publicId);
    }

    public Optional<CredentialsRecord> findCredentialsRecordByPublicId(String publicId){
        return credentialsRepository.findByPublicId(publicId);
    }

    public Optional<Credentials> findMatchingCredentials(String businessId, String uri) {

        Optional<List<Credentials>> credentialsList = findAllForBusinessId(businessId);

        if (credentialsList.isPresent()) {
            List<Credentials> companiesCredentials = credentialsList.get();

            for (Credentials companyCredentials : companiesCredentials) {
                if (companyCredentials.urlPattern() != null) {
                    Matcher matcher = Pattern.compile(companyCredentials.urlPattern()).matcher(uri);

                    if (matcher.matches()) {
                        Map<String, Integer> stringIntegerMap = matcher.namedGroups();
                        Integer scheme = stringIntegerMap.get("scheme");
                        Integer domain = stringIntegerMap.get("domain");
                        Integer path = stringIntegerMap.get("path");
                        Integer params = stringIntegerMap.get("params");

                        Map<String,String> groups = new HashMap<>();

                        if (scheme != null) {
                            String schemeGroup = matcher.group(scheme);
                            groups.put("scheme", schemeGroup);
                        }
                        if (domain != null) {
                            String domainGroup = matcher.group(domain);
                            groups.put("domain", domainGroup);
                        }
                        if (path != null) {
                            String pathGroup = matcher.group(path);
                            groups.put("path", pathGroup);
                        }
                        if (params != null) {
                            String paramsGroup = matcher.group(params);
                            groups.put("params", paramsGroup);
                        }

                        if (logger.isDebugEnabled()){
                            logger.debug("Given URL matches with identified groups {}", groups);
                        }
                        return Optional.of(companyCredentials);


                    } else {
                        logger.info("No matching url patterns in found credentials");
                    }
                } else {
                    logger.info("No url patterns in found credentials");
                }
            }
        }
        return Optional.empty();
    }
}
