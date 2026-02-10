# Kademlia DHT with Quorum Replication

Java implementation of a Kademlia Distributed Hash Table with quorum-based leaderless replication and adaptive caching (ARC).

## Requirements

- JDK 17 or 21 (Gradle 8.5 does not support Java 22+; if you have Java 25 only, install OpenJDK 21: `brew install openjdk@21` and set `JAVA_HOME`)
- No Gradle install needed: use `./gradlew` (wrapper included; see `docs/GRADLE-SETUP.md`)

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

The two-node integration test (`ServerIntegrationTest`) is disabled by default because it can hang on UDP/network. To run it manually: `./gradlew test --tests "com.kademlia.dht.ServerIntegrationTest"`.

## Architecture (High Level)

- **Entry + orchestration**: `Main` (CLI/REPL) and `Server` (high-level API) drive the node lifecycle.
- **Networking**: `UdpTransport` provides UDP request/response plumbing; `MessageCodec` encodes/decodes messages.
- **Protocol**: `KademliaProtocol` implements ping/store/find RPCs and manages the routing table.
- **Routing**: `RoutingTable` and `KBucket` implement Kademlia bucket splitting and neighbor lookup.
- **Crawling**: `SpiderCrawl`, `NodeSpiderCrawl`, and `ValueSpiderCrawl` implement iterative lookups.
- **Storage**: `IStorage` with `ForgetfulStorage` (TTL) and `ARCStorage` (adaptive cache).
- **Utilities**: `Digest` for SHA-1 IDs, `Pair` for simple tuples, `ByteArray` for keying.

## Component Interactions

### Startup and bootstrap

1. `Main` parses args and constructs `Server`.
2. `Server.listen()` creates `UdpTransport`, builds `KademliaProtocol`, and sets the transport request handler.
3. `Server.bootstrap()` pings seed nodes using `KademliaProtocol.callPing()` and starts a `NodeSpiderCrawl` to fill the routing table.

### `get` flow (lookup)

1. `Server.get()` hashes the key with `Digest` and checks `IStorage`.
2. If missing, it runs `ValueSpiderCrawl`, which calls `KademliaProtocol.callFindValue()` on nearest nodes.
3. Responses either carry a value or closer nodes; `ValueSpiderCrawl` collects values, picks the majority, and read-repairs by calling `callStore()` on the nearest node without the value.

### `set` flow (store)

1. `Server.set()` hashes the key with `Digest` and finds nearest nodes via `RoutingTable.findNeighbors()`.
2. A `NodeSpiderCrawl` refines the closest set, then `KademliaProtocol.callStore()` writes the value to those nodes.
3. `DynamicQuorum` adjusts read/write thresholds based on latency and success.

## Interaction Diagrams (ASCII)

### Startup + bootstrap

```
Main -> Server.listen()
Server.listen -> UdpTransport (bind UDP)
Server.listen -> KademliaProtocol (create, set handler)
Main -> Server.bootstrap(seeds)
Server.bootstrap -> KademliaProtocol.callPing(seed)
KademliaProtocol.callPing -> UdpTransport.send(PING)
UdpTransport -> seed (PING) -> UdpTransport (PING_RESPONSE)
Server.bootstrap -> NodeSpiderCrawl.find()
NodeSpiderCrawl -> KademliaProtocol.callFindNode()
KademliaProtocol.callFindNode -> UdpTransport.send(FIND_NODE)
UdpTransport -> peers (FIND_NODE / FIND_NODE_RESPONSE)
NodeSpiderCrawl -> RoutingTable.addContact(...)
```

### `get` flow

```
Client -> Server.get(key)
Server.get -> Digest.digest(key)
Server.get -> IStorage.get(dkey)
IStorage -> (miss)
Server.get -> ValueSpiderCrawl.find()
ValueSpiderCrawl -> KademliaProtocol.callFindValue()
KademliaProtocol.callFindValue -> UdpTransport.send(FIND_VALUE)
UdpTransport -> peers (FIND_VALUE / FIND_VALUE_RESPONSE)
ValueSpiderCrawl -> majority vote + read repair
ValueSpiderCrawl -> KademliaProtocol.callStore(nearestWithoutValue)
Server.get -> Optional<byte[]> result
```

### `set` flow

```
Client -> Server.set(key, value)
Server.set -> Digest.digest(key)
Server.set -> RoutingTable.findNeighbors(target)
Server.set -> NodeSpiderCrawl.find()
NodeSpiderCrawl -> KademliaProtocol.callFindNode()
Server.set -> KademliaProtocol.callStore(nearest)
KademliaProtocol.callStore -> UdpTransport.send(STORE)
UdpTransport -> peers (STORE / STORE_RESPONSE)
Server.set -> DynamicQuorum.adjustQuorum(...)
```