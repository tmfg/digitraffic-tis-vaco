package fi.digitraffic.tis.utilities;

import java.io.PrintWriter;
import java.io.StringWriter;

public class Conversions {
    public static String serializeThrowable(Throwable e) {
        e.printStackTrace(new PrintWriter(new StringWriter()));
        return new StringWriter().toString();
    }
}
