package fi.digitraffic.tis.utilities;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class Conversions {

    private Conversions() {}

    public static String serializeThrowable(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
