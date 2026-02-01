# Kademlia DHT with Quorum Replication

Java implementation of a Kademlia Distributed Hash Table with quorum-based leaderless replication and adaptive caching (ARC).

## Requirements

- JDK 21+
- Gradle 8.5+ (or use wrapper)

## Build

```bash
./gradlew build
```

## Run

```bash
./gradlew run --args="--port=8468"
./gradlew run --args="--help"
```

## Test

```bash
./gradlew test
```

## Architecture

- **node**: NodeId, Node, NodeHeap
- **routing**: KBucket, RoutingTable
- **storage**: IStorage, ForgetfulStorage, ARCStorage
- **protocol**: RPC messages, MessageCodec, KademliaProtocol
- **network**: UdpTransport, DynamicQuorum, Server
- **crawling**: SpiderCrawl, NodeSpiderCrawl, ValueSpiderCrawl
