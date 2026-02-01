package com.kademlia.dht;

import com.kademlia.dht.network.Server;
import com.kademlia.dht.util.Pair;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * Entry point for Kademlia DHT. CLI and interactive REPL.
 */
public class Main {
    public static void main(String[] args) {
        Map<String, String> opts = parseArgs(args);
        if (opts.containsKey("help") || opts.containsKey("h")) {
            printHelp();
            return;
        }
        try {
            int port = Integer.parseInt(opts.getOrDefault("port", "8468"));
            String iface = opts.getOrDefault("interface", "0.0.0.0");
            String bootstrapStr = opts.get("bootstrap");

            Server server = new Server(20, 3, null, null);
            server.listen(port, iface);

            if (bootstrapStr != null) {
                List<Pair<String, Integer>> bootstrap = Arrays.stream(bootstrapStr.split(","))
                        .map(addr -> {
                            String[] parts = addr.split(":");
                            return Pair.of(parts[0].trim(), Integer.parseInt(parts[1].trim()));
                        })
                        .toList();
                server.bootstrap(bootstrap).get(30, TimeUnit.SECONDS);
                System.out.println("Bootstrapped with " + bootstrap.size() + " nodes");
            }

            System.out.println("Kademlia DHT listening on " + iface + ":" + port);
            System.out.println("Node ID: " + server.getSelfNode().id().toBigInteger());

            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    System.out.print("> ");
                    if (!scanner.hasNextLine()) break;
                    String line = scanner.nextLine();
                    if (line == null || line.isBlank()) continue;
                    String[] parts = line.trim().split("\\s+", 2);
                    String cmd = parts[0].toLowerCase();
                    switch (cmd) {
                        case "get" -> {
                            if (parts.length < 2) {
                                System.out.println("Usage: get <key>");
                                continue;
                            }
                            Optional<byte[]> value = server.get(parts[1].trim()).get(10, TimeUnit.SECONDS);
                            System.out.println(value.map(String::new).orElse("(not found)"));
                        }
                        case "set" -> {
                            if (parts.length < 2) {
                                System.out.println("Usage: set <key>=<value>");
                                continue;
                            }
                            String rest = parts[1].trim();
                            int eq = rest.indexOf('=');
                            if (eq <= 0) {
                                System.out.println("Usage: set <key>=<value>");
                                continue;
                            }
                            String key = rest.substring(0, eq).trim();
                            String val = rest.substring(eq + 1).trim();
                            boolean success = server.set(key, val.getBytes()).get(10, TimeUnit.SECONDS);
                            System.out.println(success ? "Stored" : "Failed");
                        }
                        case "quit", "exit" -> {
                            server.close();
                            return;
                        }
                        default -> System.out.println("Unknown command: " + cmd + ". Use get, set, quit, exit.");
                    }
                }
            }
            server.close();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printHelp() {
        System.out.println("Kademlia DHT - Distributed Hash Table with Quorum Replication");
        System.out.println();
        System.out.println("Usage: [options]");
        System.out.println("  --port=N          UDP port (default: 8468)");
        System.out.println("  --interface=ADDR  Bind address (default: 0.0.0.0)");
        System.out.println("  --bootstrap=HOST:PORT[,HOST:PORT]  Bootstrap nodes");
        System.out.println("  --help, -h        Show this help");
        System.out.println();
        System.out.println("REPL commands: get <key>, set <key>=<value>, quit, exit");
    }

    static Map<String, String> parseArgs(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (String arg : args) {
            String s = arg.replaceFirst("^--", "");
            String[] parts = s.split("=", 2);
            opts.put(parts[0], parts.length > 1 ? parts[1] : "true");
        }
        return opts;
    }
}
