package fi.digitraffic.tis.utilities;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper for working with Java Streams to reduce boilerplate, e.g. instead of
 *
 * <pre>
 *     entry.validations().stream().map(ValidationInput::name).collect(Collectors.toSet())
 * </pre>
 *
 * with this we can have
 *
 * <pre>
 *     Streams.map(entry.validations(), ValidationInput::name).toSet();
 * </pre>
 *
 * {@link Streams} contains further wrapped stream operations.
 */
public class Streams {
    public static <O,R> Chain<R> map(Collection<O> objects, Function<O, R> mapper) {
        return new Chain<>(objects.stream().map(mapper));
    }

    public static <O> Chain<O> filter(Collection<O> objects, Predicate<? super O> predicate) {
        return new Chain<>(objects.stream().filter(predicate));
    }

    /**
     * Java Streams are internally modeled as pipelines, <code>Chain</code> mimics this by providing shorthands to
     * operations which would need multiple steps/calls otherwise. {@link #stream()} exposes the underlying {@link Stream}
     * for those cases where the needed operation isn't exposed in this wrapper and/or the extra calls doesn't make
     * client side code uglier.
     *
     * @param stream
     * @param <R>
     */
    public static class Chain<R> {

        private Stream<R> stream;

        public Chain(Stream<R> stream) {
            this.stream = stream;
        }

        public Stream<R> stream() {
            return stream;
        }

        public Set<R> toSet() {
                return stream.collect(Collectors.toSet());
            }

        public List<R> toList() {
            return stream.toList();
        }

        public Chain<R> filter(Predicate<? super R> predicate) {
            stream = stream.filter(predicate);
            return this;
        }

        public <O> Chain<O> map(Function<R, O> mapper) {
            return new Chain<O>(stream.map(mapper));
        }
    }
}
