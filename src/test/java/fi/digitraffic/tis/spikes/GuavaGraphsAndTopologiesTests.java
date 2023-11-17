package fi.digitraffic.tis.spikes;

import com.google.common.collect.ImmutableList;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import fi.digitraffic.tis.utilities.MoreGraphs;
import org.junit.Ignore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class GuavaGraphsAndTopologiesTests {

    @Test
    @Ignore("testing out how the API works and looks, no asserts -> no point running this in automation")
    void topologicalSorting() {
        MutableGraph<Node> g = GraphBuilder.directed().build();
        Node dl = new Node("download", -1, List.of());
        Node rs = new Node("rulesets", -1, List.of(dl));
        Node rx = new Node("rule_x", -1, List.of(rs));
        Node ry = new Node("rule_y", -1, List.of(rs));
        Node co = new Node("complete", -1, List.of(rx, ry));
        Node cl = new Node("cleanup", -1, List.of(co));

        List<Node> nodes = List.of(dl, rs, rx, ry, co, cl);

        nodes.forEach(g::addNode);
        nodes.forEach(n -> n.deps.forEach(d -> g.putEdge(d, n)));

        ImmutableList<Node> order = MoreGraphs.topologicalOrdering(g);

        Set<String> previousGroupNodes = new HashSet<>();
        List<Node> finalNodes = new ArrayList<>();
        int prioGroup = 1;
        int groupIndex = 0;
        for (Node n : order) {
            if (n.deps().stream().anyMatch(d -> previousGroupNodes.contains(d.name()))) {
                prioGroup++;
                groupIndex = 0;
                previousGroupNodes.clear();
            }
            finalNodes.add(new Node(n.name, prioGroup * 100 + groupIndex, n.deps));

            previousGroupNodes.add(n.name());
            groupIndex++;
        }

        finalNodes.forEach(System.out::println);
    }

    record Node(String name, int prio, List<Node> deps) {}
}
