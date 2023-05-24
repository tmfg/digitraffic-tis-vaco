package fi.digitraffic.tis.vaco.queuehandler.model;

import jakarta.annotation.Nullable;
import org.immutables.value.Value;
import org.immutables.value.Value.Immutable;
import org.springframework.context.annotation.Primary;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigInteger;

@Table("test")
@Immutable
@Value.Style(passAnnotations = Primary.class)
public abstract class Yay {

    @Column("id")
    @Id
    //@Value.Parameter
    @Nullable
    public abstract BigInteger getId();

    @Column("name")
   // @Value.Parameter
    public abstract String getName();
}
