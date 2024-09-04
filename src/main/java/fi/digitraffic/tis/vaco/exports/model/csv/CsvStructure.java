package fi.digitraffic.tis.vaco.exports.model.csv;

import org.immutables.value.Value;

import java.util.List;

/**
 * Container for describing the structure of a CSV file.
 */
@Value.Immutable
public interface CsvStructure {

    @Value.Parameter
    String fileName();

    @Value.Parameter
    List<String> columns();

}
