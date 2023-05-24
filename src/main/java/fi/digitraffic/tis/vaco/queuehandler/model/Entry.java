package fi.digitraffic.tis.vaco.queuehandler.model;


import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigInteger;

@Table("queue_entry")
public record Entry(
    @Id
    @Column
    BigInteger id,

    @ReadOnlyProperty
    String publicId,

    @NotBlank
    @Column
    String format,

    @NotBlank
    @Column
    String url,

    @Column
    String etag,

    @Column
    JsonNode metadata
) {
}
