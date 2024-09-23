package fi.digitraffic.tis.utilities;

import java.util.function.Function;

public class Functional {
    @SafeVarargs
    public static <K, V> V firstNonNull(Function<K, V> lookup, K... keys) {
        for (K key : keys) {
            V applied = lookup.apply(key);
            if (applied != null) {
                return applied;
            }
        }
        return null;
    }
}
