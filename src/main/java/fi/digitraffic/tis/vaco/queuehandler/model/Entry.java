package fi.digitraffic.tis.vaco.queuehandler.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import javax.validation.constraints.NotNull;
import java.math.BigInteger;

@Table("queue_entry")
public record Entry(
    @Id
    @Column
    BigInteger id,

    @Column
    String publicId,

    @NotNull
    @Column
    String format,

    @NotNull
    @Column
    String url,

    @Column
    String etag
) {
}
