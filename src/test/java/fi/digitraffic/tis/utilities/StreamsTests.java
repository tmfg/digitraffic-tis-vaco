package fi.digitraffic.tis.utilities;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class StreamsTests {

    @Test
    void toListReturnsModifiableList() {
        // immutable/unmodifiable is nice in principle, but Java is not immutable first and this method masks such
        // details so for further utility this tests ensures the Chain#toList() always returns a modifiable list
        List<Integer> amended = Streams.map(List.of(1, 2, 3), i -> --i).toList();
        amended.addAll(List.of(3, 4));
        assertThat(amended, equalTo(List.of(0, 1, 2, 3, 4)));
    }

    @Test
    void toSetReturnsModifiableSet() {
        // similar to above
        Set<String> letters = new HashSet<>(List.of("a", "a", "b", "c", "d", "d", "d", "e"));
        Set<String> amended = Streams.map(letters, String::toUpperCase).toSet();
        amended.addAll(Set.of("X"));
        assertThat(amended, equalTo(Set.of("A", "B", "C", "D", "E", "X")));
    }

    @Test
    void canWorkWithLists() {
        assertThat(Streams.map(List.of(1, 2, 3, 4, 5), i -> --i).toList(),
            equalTo(List.of(0, 1, 2, 3, 4)));
        assertThat(Streams.filter(List.of(1, 2, 3, 4, 5), i -> i > 3).toList(),
            equalTo(List.of(4, 5)));
    }

    @Test
    void canWorkWithArrays() {
        Boolean[] booleans = new Boolean[] { true, false, true};
        assertThat(Streams.map(booleans, b -> !b).toList(),
            equalTo(List.of(false, true, false)));
        assertThat(Streams.filter(booleans, b -> b).toList(),
            equalTo(List.of(true, true)));
    }

    @Test
    void canWorkWithEnumerations() {
        assertThat(Streams.map(new StringTokenizer("legacy APIs supported"), t -> t.toString().toUpperCase()).toList(),
            equalTo(List.of("LEGACY", "APIS", "SUPPORTED")));
        assertThat(Streams.filter(new StringTokenizer("legacy APIs supported"), t -> t.toString().length() > 5).toList(),
            equalTo(List.of("legacy", "supported")));
    }

    @Test
    void canChainListThroughCommonOperations() {
        assertThat(Streams.map(List.of(0, 1, 2, 3, 4), i -> ++i)
                .filter(i -> i > 3)
                .toList(),
            equalTo(List.of(4, 5)));
    }

    @Test
    void canChainArrayThroughCommonOperations() {
        assertThat(Streams.map(new Boolean[] {true, false, true}, b -> !b)
                .filter(b -> b)
                .toList(),
            equalTo(List.of(true)));
    }

    @Test
    void canChainEnumerationThroughCommonOperations() {
        assertThat(Streams.map(new StringTokenizer("I'm a little teapot"), s -> s.toString() + "!")
                .filter(s -> s.length() > 5)
                .toList(),
            equalTo(List.of("little!", "teapot!")));
    }

    @Test
    void typesUpdateAccordinglyWhenOperationsAreChained() {
        assertThat(Streams.map(List.of("watermelons", "bananas", "mangos"), String::length)
                .map(i -> i * 2)
                .map(i -> i > 12)
                .toList(),
            equalTo(List.of(true, true, false)));

    }
}
