package fi.digitraffic.tis.vaco.delegator.model;

public enum TaskCategory {

    VALIDATION(1),
    CONVERSION(2),
    RULE(3);

    private final int priority;

    private final String name;

    TaskCategory(int priority) {
        this.priority = priority;
        this.name = this.name().toLowerCase();
    }

    public int getPriority() {
        return priority;
    }

    public String getName() {
        return name;
    }
}
