package fi.digitraffic.tis.utilities;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

class StreamsTests {

    @Test
    void toListReturnsModifiableList() {
        // immutable/unmodifiable is nice in principle, but Java is not immutable first and this method masks such
        // details so for further utility this tests ensures the Chain#toList() always returns a modifiable list
        List<Integer> amended = Streams.map(List.of(1, 2, 3), i -> --i).toList();
        amended.addAll(List.of(3, 4));
        assertThat(amended, equalTo(List.of(0, 1, 2, 3, 4)));
    }
}
