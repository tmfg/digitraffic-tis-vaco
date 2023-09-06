package fi.digitraffic.tis.vaco.rules;

import fi.digitraffic.tis.vaco.VacoException;

/**
 * Common wrapping supertype for all non-recoverable exceptions thrown during rule execution.
 */
public class RuleExecutionException extends VacoException {
    public RuleExecutionException(String message) {
        super(message);
    }

    public RuleExecutionException(String message, Exception cause) {
        super(message, cause);
    }
}
