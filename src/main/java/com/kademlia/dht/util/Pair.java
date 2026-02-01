package com.kademlia.dht.util;

import java.util.Objects;

/**
 * Immutable pair of two values. Used for bucket split results and heap entries.
 */
public record Pair<L, R>(L left, R right) {
    public static <L, R> Pair<L, R> of(L left, R right) {
        return new Pair<>(left, right);
    }
}
