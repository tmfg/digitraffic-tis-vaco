package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.ContextRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.db.repositories.ContextRepository;
import fi.digitraffic.tis.vaco.ui.model.Context;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ContextService {
    private final ContextRepository contextRepository;
    private final RecordMapper recordMapper;
    private final CompanyRepository companyRepository;

    public ContextService(ContextRepository contextRepository, RecordMapper recordMapper, CompanyRepository companyRepository) {
        this.contextRepository = Objects.requireNonNull(contextRepository);
        this.recordMapper = Objects.requireNonNull(recordMapper);
        this.companyRepository = Objects.requireNonNull(companyRepository);
    }

    public List<Context> findByBusinessId(String businessId) {
        List<ContextRecord> contextRecords = contextRepository.findByBusinessId(businessId);
        return Streams.map(contextRecords, contextRecord -> recordMapper.toContext(contextRecord, businessId)).toList();
    }

    public Optional<Context> find(String context, String businessId) {
        Optional<CompanyRecord> companyRecord = companyRepository.findByBusinessId(businessId);
        return companyRecord
            .flatMap(cr -> contextRepository.find(context, cr.id())
            .map(contextRecord -> recordMapper.toContext(contextRecord, businessId)));
    }

    public List<Context> create(Context context) {
        Optional<CompanyRecord> companyRecord = companyRepository.findByBusinessId(context.businessId());
        contextRepository.create(context.context(), companyRecord.get().id());
        return findByBusinessId(context.businessId());
    }

    public List<Context> update(String oldContext, Context context) {
        Optional<CompanyRecord> companyRecord = companyRepository.findByBusinessId(context.businessId());
        contextRepository.update(oldContext, context.context(), companyRecord.get().id());
        return findByBusinessId(context.businessId());
    }
}
