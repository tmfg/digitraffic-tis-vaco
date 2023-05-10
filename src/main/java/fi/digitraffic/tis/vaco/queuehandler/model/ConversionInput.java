package fi.digitraffic.tis.vaco.queuehandler.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import javax.validation.constraints.NotBlank;
import java.math.BigInteger;

@Table("queue_conversion_input")
public record ConversionInput(
    @Id
    @Column
    BigInteger id,

    @NotBlank
    @Column
    BigInteger entryId
) {
}
