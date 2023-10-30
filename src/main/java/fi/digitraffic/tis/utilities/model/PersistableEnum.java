package fi.digitraffic.tis.utilities.model;

/**
 * Interface for enums which should map 1:1 to database and back.
 */
public interface PersistableEnum {

    /**
     * Value of the enum entry in database. Must match case sensitively 1:1 with the matching database enum entry.
     * @return Value of the field.
     */
    String fieldName();
}
