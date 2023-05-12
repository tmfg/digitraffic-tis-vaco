package fi.digitraffic.tis.vaco.queuehandler.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import jakarta.validation.constraints.NotBlank;
import java.math.BigInteger;

@Table("queue_generated_file")
public record GeneratedFile(
    @Id
    @Column
    BigInteger id,

    @NotBlank
    @Column
    BigInteger entryId,

    @Column
    String path
) {
}
