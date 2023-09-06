package fi.digitraffic.tis.vaco.rules.conversion.echo;

import fi.digitraffic.tis.vaco.rules.conversion.ConverterRule;

public class EchoConverterRule extends ConverterRule {
    public static final String RULE_NAME = "echo";

    @Override
    public String getIdentifyingName() {
        return RULE_NAME;
    }
}
