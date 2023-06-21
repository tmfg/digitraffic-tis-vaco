package fi.digitraffic.tis.spikes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class CollectionsBehaviorTests {
    @Test
    void removingSetEntriesFromLists() {
        Set<Integer> nums = Set.of(3, 1, 2);
        List<Integer> more = new ArrayList<>();
        more.addAll(List.of(1, 1, 2, 3, 3, 3, 5, 4, 2, 1, 6));
        more.removeAll(nums);
        assertThat(more, equalTo(List.of(5, 4, 6)));
    }
}
