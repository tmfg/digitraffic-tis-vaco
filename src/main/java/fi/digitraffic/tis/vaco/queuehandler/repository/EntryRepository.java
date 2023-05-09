package fi.digitraffic.tis.vaco.queuehandler.repository;

import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;

@Repository
public interface EntryRepository extends CrudRepository<Entry, BigInteger> {

    @Query("SELECT * FROM queue_entry WHERE public_id=:publicId")
    Entry findByPublicId(@Param("publicId") String publicId);
}
