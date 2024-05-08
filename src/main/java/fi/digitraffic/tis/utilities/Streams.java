package fi.digitraffic.tis.utilities;

import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
public final class Streams {

    private Streams() {}

    /**
     * Functionally equivalent to {@link Stream#map(Function)}. Null safe. Shorthand for
     * <pre>
     *     Stream&lt;O&gt; result;
     *     if (objects == null) {
     *         result = Stream.&lt;O&gt;empty().map(mapper);
     *     } else {
     *         result = objects.stream().map(mapper);
     *     }
     * </pre>
     * @param objects Objects to process.
     * @param mapper Mapper function with <code>java.util.Stream</code> compatible signature.
     * @return Chain of mapped objects.
     * @param <I> Type for input objects.
     * @param <O> Type for output objects.
     */
    public static <I, O> Chain<O> map(@Nullable Collection<I> objects, Function<? super I, ? extends O> mapper) {
        if (objects == null) {
            return new Chain<>(Stream.<I>empty().map(mapper));
        } else {
            return new Chain<>(objects.stream().map(mapper));
        }
    }

    /**
     * Functionally equivalent to {@link Stream#filter(Predicate)}. Shorthand for
     * <pre>
     *     Arrays.stream(objects).filter(predicate)
     * </pre>
     * @param objects Objects to process.
     * @param mapper Mapper function with <code>java.util.Stream</code> compatible signature.
     * @return Chain of mapped objects.
     * @param <I> Type for input objects.
     * @param <O> Type for output objects.
     */
    public static <I, O> Chain<O> map(I[] objects, Function<? super I, ? extends O> mapper) {
        return new Chain<>(Arrays.stream(objects).map(mapper));
    }

    /**
     * No direct functional equivalence due to custom internal {@link Spliterator} implementation. Behavior matches with
     * any other matching overload, e.g. {@link #map(Collection, Function)}.
     * @param objects Objects to process.
     * @param mapper Mapper function with <code>java.util.Stream</code> compatible signature.
     * @return Chain of mapped objects.
     * @param <I> Type for input objects.
     * @param <O> Type for output objects.
     * @see #asStream(Enumeration)
     */
    public static <I, O> Chain<O> map(Enumeration<I> objects, Function<? super I, ? extends O> mapper) {
        return new Chain<>(asStream(objects)).map(mapper);
    }

    /**
     * Functionally equivalent to {@link Stream#flatMap(Function)}. Shorthand for
     *
     * <pre>
     *     objects.stream().flatMap(o -> mapper.apply(o).stream())
     * </pre>
     * @param objects Objects to process.
     * @param mapper Mapper which converts single object to a collections of derived objects.
     * @return Chain of flattened derived objects.
     * @param <I> Type for input objects.
     * @param <O> Type for derived output objects.
     */
    public static <I,O> Chain<O> flatten(Collection<I> objects, Function<? super I, ? extends Collection<O>> mapper) {
        return new Chain<>(objects.stream()).flatten(mapper);
    }

    /**
     * Functionally equivalent to {@link Stream#filter(Predicate)}. Shorthand for
     * <pre>
     *     objects.stream().filter(predicate)
     * </pre>
     * @param objects Objects to process.
     * @param predicate Predicate function with <code>java.util.Stream</code> compatible signature.
     * @return Chain of filtered objects.
     * @param <O> Type for output objects.
     */
    public static <O> Chain<O> filter(Collection<O> objects, Predicate<? super O> predicate) {
        return new Chain<>(objects.stream().filter(predicate));
    }

    /**
     * Functionally equivalent to {@link Stream#filter(Predicate)}. Shorthand for
     * <pre>
     *     Arrays.stream(objects).filter(predicate)
     * </pre>
     * @param objects Objects to process.
     * @param predicate Predicate function with <code>java.util.Stream</code> compatible signature.
     * @return Chain of filtered objects.
     * @param <O> Type for output objects.
     */
    public static <O> Chain<O> filter(O[] objects, Predicate<? super O> predicate) {
        return new Chain<>(Arrays.stream(objects).filter(predicate));
    }

    /**
     * No direct functional equivalence due to custom internal {@link Spliterator} implementation. Behavior matches with
     * any other matching overload, e.g. {@link #filter(Collection, Predicate)}
     * @param objects Objects to process.
     * @param predicate Predicate function with <code>java.util.Stream</code> compatible signature.
     * @return Chain of filtered objects.
     * @param <O> Type for output objects.
     * @see #asStream(Enumeration)
     */
    public static <O> Chain<O> filter(Enumeration<O> objects, Predicate<? super O> predicate) {
        return new Chain<>(asStream(objects)).filter(predicate);
    }

    /**
     * Collect (=convert) given collection to Map using provided mapper functions. Shorthand for
     * <pre>
     *     objects.stream().collect(Collectors.toMap(keyMapper, valueMapper)
     * </pre>
     * @param objects Objects to process.
     * @param keyMapper Mapping function to produce keys.
     * @param valueMapper Mapping function to produce values.
     * @return Map of converted objects.
     * @param <I> Type for input objects.
     * @param <K> Type for output map's keys.
     * @param <V> Type for output map's values.
     */
    public static <I, K, V> Map<K, V> collect(
        Collection<I> objects,
        Function<? super I, K> keyMapper,
        Function<? super I, V> valueMapper) {
        return new Chain<>(objects.stream()).collect(keyMapper, valueMapper);
    }

    /**
     *  Collect (=convert) given collection transforming each entry using the provided transformation function. Shorthand
     * for
     * <pre>
     *     Streams.map(objects, mapper).toList()
     * </pre>
     * @param objects Objects to process.
     * @param mapper Mapper function with <code>java.util.Stream</code> compatible signature.
     * @return List of converted objects.
     * @param <I> Type for input objects.
     * @param <O> Type for output objects.
     */
    public static <I, O> List<O> collect(Collection<I> objects, Function<? super I, O> mapper) {
        return map(objects, mapper).toList();
    }

    /**
     * {@link Set} overload of {@link #collect(Collection, Function)} to keep the type as close to original as possible.

     * @param objects Objects to process.
     * @param mapper Mapper function with <code>java.util.Stream</code> compatible signature.
     * @return Set of converted objects.
     * @param <I> Type for input objects.
     * @param <O> Type for output objects.
     */
    public static <I, O> Set<O> collect(Set<I> objects, Function<? super I, O> mapper) {
        return map(objects, mapper).toSet();
    }

    /**
     * Create one concatenated stream from given collections. Non-recursive, eager implementation.
     * @param first First collection to wrap. This is provided separately to make the compiler do some helpful work for
     *              us.
     * @param more Remaining collections to concatenate in order.
     * @return Concatenated collections as single flat stream.
     * @param <T> Common type for all the contained objects.
     */
    @SafeVarargs
    public static <T> Chain<T> concat(Collection<T> first, Collection<T>... more) {
        Stream<T> merged = first.stream();

        for (Collection<? extends T> extra : more) {
            merged = Stream.concat(merged, extra.stream());
        }

        return new Chain<>(merged);
    }

    /**
     * Similar to {@link #concat(Collection, Collection[])} but for {@link Stream Streams}. See javadoc of that method
     * for more in-depth explanation.
     *
     * @param first First stream to concatenate.
     * @param more Remaining streams.
     * @return Concatenated streams.
     * @param <T> Common type for contained objects.
     */
    @SafeVarargs
    public static <T> Chain<T> concat(Stream<T> first, Stream<T>... more) {
        Stream<T> merged = first;

        for (Stream<T> extra : more) {
            merged = Stream.concat(merged, extra);
        }

        return new Chain<>(merged);
    }

    /**
     * Custom {@link Spliterator} wrapping for legacy type {@link Enumeration} to provide a bridge to Java
     * {@link Stream} with {@link StreamSupport}.
     * @param objects Objects to process.
     * @return <code>java.util.Enumeration</code> wrapped as <code>java.util.Stream</code>
     * @param <I> Type for input objects.
     */
    private static <I> Stream<I> asStream(Enumeration<I> objects) {
        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(objects.asIterator(), Spliterator.ORDERED),
            false
        );
    }

    /**
     * Group given collection using the provided classifier function. Shorthand for
     * <pre>
     *     objects.stream()
     *         .collect(Collectors.groupingBy(classifier));
     * </pre>
     * @param objects Objects to process.
     * @param classifier Classifier to use when grouping.
     * @return Map of grouped objects by classifier.
     * @param <C> Classifier type.
     * @param <I> Input object type.
     */
    public static <C, I> Map<C, List<I>> groupBy(Collection<I> objects, Function<? super I, ? extends C> classifier) {
        return objects
            .stream()
            .collect(Collectors.groupingBy(classifier));
    }

    /**
     * Java Streams are internally modeled as pipelines, <code>Chain</code> mimics this by providing shorthands to
     * operations which would need multiple steps/calls otherwise. {@link #stream()} exposes the underlying {@link Stream}
     * for those cases where the needed operation isn't exposed in this wrapper and/or the extra calls doesn't make
     * client side code uglier.
     *
     * @param <R> Type for objects wrapped within chain's internal stream.
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
            return stream.collect(Collectors.toCollection(ArrayList::new));
        }

        public Chain<R> filter(Predicate<? super R> predicate) {
            stream = stream.filter(predicate);
            return this;
        }

        public <O> Chain<O> map(Function<? super R, ? extends O> mapper) {
            return new Chain<>(stream.map(mapper));
        }

        public Optional<R> findFirst() {
            return stream().findFirst();
        }

        /**
         * Force evaluation of the currently wrapped stream without producing return values. Might throw exceptions
         * based on underlying process.
         */
        public void complete() {
            stream.forEach(action -> {});
        }

        public <K, V> Map<K, V> collect(
            Function<? super R, K> keyMapper,
            Function<? super R, V> valueMapper) {
            return stream.collect(Collectors.toMap(keyMapper, valueMapper));
        }

        public <O> Chain<O> flatten(Function<? super R, ? extends Collection<O>> mapper) {
            return new Chain<>(stream.flatMap(r -> mapper.apply(r).stream()));
        }
    }

    public static <T> List<T> append(List<T> list, T arg){
        return Stream.concat(list.stream(), Stream.of(arg)).toList();
    }

}
