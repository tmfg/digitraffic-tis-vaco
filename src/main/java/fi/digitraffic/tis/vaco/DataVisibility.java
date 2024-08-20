package fi.digitraffic.tis.vaco;

/**
 * Define visibility scopes for JSON fields. See uses of the subinterfaces.
 */
public final class DataVisibility {
    private DataVisibility() {}

    /**
     * Public/stable data which can be exposed to users.
     */
    public interface Public {}

    public interface AdminRestricted {}

    /**
     * Private/internal data, such as database id:s, which we do not want to expose.
     */
    public interface InternalOnly {}

    /**
     * Data which can be exceptionally exposed in webhook payloads. Use sparingly.
     */
    public interface Webhook {}
}
