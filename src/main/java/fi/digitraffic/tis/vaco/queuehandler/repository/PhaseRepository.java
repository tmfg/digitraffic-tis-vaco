package fi.digitraffic.tis.vaco.queuehandler.repository;

import fi.digitraffic.tis.vaco.queuehandler.model.Phase;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;
import java.util.List;

@Repository
public interface PhaseRepository extends CrudRepository<Phase, BigInteger> {

    @Query("SELECT * from queue_phase WHERE entry_id=:entryId")
    List<Phase> findByEntryId(@Param("entryId") BigInteger entryId);
}
