package com.kademlia.dht.routing;

import com.kademlia.dht.node.Node;
import com.kademlia.dht.node.NodeId;
import com.kademlia.dht.util.Digest;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoutingTableTest {

    private Node selfNode() throws Exception {
        return new Node(
                new NodeId(Digest.hash("self")),
                InetAddress.getByName("127.0.0.1"),
                8468
        );
    }

    private Node randomNode(int seed) throws Exception {
        byte[] id = Digest.hash("node" + seed);
        return new Node(new NodeId(id), InetAddress.getByName("127.0.0.1"), 8468 + seed);
    }

    @Test
    void testFindNeighbors() throws Exception {
        Node self = selfNode();
        RoutingTable table = new RoutingTable(self, 20);
        for (int i = 0; i < 100; i++) {
            table.addContact(randomNode(i));
        }
        Node target = randomNode(999);
        List<Node> neighbors = table.findNeighbors(target, 20);
        assertTrue(neighbors.size() <= 20);
        for (int i = 1; i < neighbors.size(); i++) {
            assertTrue(neighbors.get(i - 1).distanceTo(target) <= neighbors.get(i).distanceTo(target));
        }
    }

    @Test
    void testAddContact() throws Exception {
        Node self = selfNode();
        RoutingTable table = new RoutingTable(self, 20);
        table.addContact(randomNode(1));
        assertTrue(table.getNodeCount() >= 1);
    }
}
