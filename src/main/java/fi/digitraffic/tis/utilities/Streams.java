package fi.digitraffic.tis.utilities;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper for reducing boilerplate with Java Streams, e.g. instead of
 *
 * <pre>
 *     entry.validations().stream().map(ValidationInput::name).collect(Collectors.toSet())
 * </pre>
 *
 * we can have
 *
 * <pre>
 *     Streams.map(entry.validations(), ValidationInput::name).toSet();
 * </pre>
 *
 * {@link Chain} contains further wrapped stream operations.
 */
public class Streams {

    /**
     * Functionally equivalent to {@link Stream#map(Function)}. Shorthand for
     * <pre>
     *     objects.stream().map(mapper)
     * </pre>
     * @param objects
     * @param mapper
     * @return
     * @param <O>
     * @param <R>
     */
    public static <I, O> Chain<O> map(Collection<I> objects, Function<? super I, ? extends O> mapper) {
        return new Chain<>(objects.stream().map(mapper));
    }

    /**
     * Functionally equivalent to {@link Stream#filter(Predicate)}. Shorthand for
     * <pre>
     *     objects.stream().filter(predicate)
     * </pre>
     * @param objects
     * @param predicate
     * @return
     * @param <O>
     */
    public static <O> Chain<O> filter(Collection<O> objects, Predicate<? super O> predicate) {
        return new Chain<>(objects.stream().filter(predicate));
    }

    /**
     * Functionally equivalent to {@link Stream#filter(Predicate)}. Shorthand for
     * <pre>
     *     Arrays.stream(objects).filter(predicate)
     * </pre>
     * @param objects
     * @param predicate
     * @return
     * @param <O>
     */
    public static <O> Chain<O> filter(O[] objects, Predicate<? super O> predicate) {
        return new Chain<>(Arrays.stream(objects).filter(predicate));
    }

    /**
     * Collect (=convert) given collection to Map using provided mapper functions. Shorthand for
     * <pre>
     *     objects.stream().collect(Collectors.toMap(keyMapper, valueMapper)
     * </pre>
     *
     * @param objects
     * @param keyMapper
     * @param valueMapper
     * @return
     */
    public static <I, K, V> Map<K, V> collect(
        Collection<I> objects,
        Function<? super I, K> keyMapper,
        Function<? super I, ? extends V> valueMapper) {
        return objects.stream().collect(Collectors.toMap(keyMapper, valueMapper));
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
    public static final class Chain<R> {

        private Stream<R> stream;

        private Chain(Stream<R> stream) {
            this.stream = stream;
        }

        public Stream<R> stream() {
            return stream;
        }

        public Set<R> toSet() {
                return stream.collect(Collectors.toSet());
            }

        public List<R> toList() {
            return stream.collect(Collectors.toList());
        }

        public Chain<R> filter(Predicate<? super R> predicate) {
            stream = stream.filter(predicate);
            return this;
        }

        public <O> Chain<O> map(Function<? super R, ? extends O> mapper) {
            return new Chain<O>(stream.map(mapper));
        }

        public Optional<R> findFirst() {
            return stream().findFirst();
        }
    }
}
