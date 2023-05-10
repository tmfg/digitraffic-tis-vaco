package fi.digitraffic.tis.vaco.queuehandler.repository;

import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;

@Repository
public interface ConversionInputRepository extends CrudRepository<ConversionInput, BigInteger> {

    ConversionInput findByEntryId(BigInteger entryId);
}
