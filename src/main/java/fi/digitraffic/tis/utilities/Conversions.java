package fi.digitraffic.tis.utilities;

import java.io.PrintWriter;
import java.io.StringWriter;

public class Conversions {
    public static String serializeThrowable(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
