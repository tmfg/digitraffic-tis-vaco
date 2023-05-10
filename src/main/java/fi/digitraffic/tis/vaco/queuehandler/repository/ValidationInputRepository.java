package fi.digitraffic.tis.vaco.queuehandler.repository;

import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;

@Repository
public interface ValidationInputRepository extends CrudRepository<ValidationInput, BigInteger> {
}
