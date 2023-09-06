package fi.digitraffic.tis.vaco.delegator.model;

public enum TaskCategory {

    VALIDATION(1),
    CONVERSION(2);

    public final int priority;

    public final String name;

    TaskCategory(int priority) {
        this.priority = priority;
        this.name = this.name().toLowerCase();
    }

}
