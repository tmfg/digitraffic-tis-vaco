package fi.digitraffic.tis.vaco.delegator.model;

public enum TaskCategory {

    VALIDATION(1),
    CONVERSION(2);

    public final int priority;

    TaskCategory(int priority) {
        this.priority = priority;
    }

}
