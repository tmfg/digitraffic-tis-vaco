package fi.digitraffic.tis.vaco.validation.model;

/**
 * Interface for enums which should map 1:1 to database and back.
 */
public interface PersistableEnum {
    String fieldName();
}