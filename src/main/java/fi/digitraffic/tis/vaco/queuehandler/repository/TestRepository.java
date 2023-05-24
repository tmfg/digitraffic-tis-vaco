package fi.digitraffic.tis.vaco.queuehandler.repository;

import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableYay;
import org.springframework.data.repository.CrudRepository;

import java.math.BigInteger;

public interface TestRepository extends CrudRepository<ImmutableYay, BigInteger> {
}
