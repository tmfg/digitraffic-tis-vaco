package fi.digitraffic.tis.vaco.notifications.model;

public enum NotificationType {
    ENTRY_COMPLETE_V1(Name.ENTRY_COMPLETE_V1);

    private final String typeName;

    NotificationType(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeName() {
        return typeName;
    }

    public static class Name {
        public static final String ENTRY_COMPLETE_V1 = "entry.complete.v1";

        private Name() {}
    }
}
