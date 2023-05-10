package fi.digitraffic.tis.vaco.queuehandler.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import javax.validation.constraints.NotBlank;
import java.math.BigInteger;
import java.sql.Timestamp;

@Table("queue_phase")
public record Phase(
    @Id
    @Column
    BigInteger id,

    @NotBlank
    @Column
    BigInteger entryId,

    @NotBlank
    @Column
    PhaseName name,

    @Column
    Timestamp started
) {
}
