package fi.digitraffic.tis.vaco.queuehandler.repository;

import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableConversionInput;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;

@Repository
public interface ConversionInputRepository extends CrudRepository<ImmutableConversionInput, BigInteger> {

    ImmutableConversionInput findByEntryId(BigInteger entryId);
}
