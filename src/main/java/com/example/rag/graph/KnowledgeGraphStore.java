package com.example.rag.graph;

import com.example.rag.config.DatabasePool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KnowledgeGraphStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Object WRITE_LOCK = new Object();

    private static Connection getConnection() throws SQLException {
        return DatabasePool.getConnection();
    }

    public KnowledgeGraphStore() {
        initSchema();
    }

    private void initSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS kg_graph (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    source_file TEXT,
                    description TEXT,
                    node_count INTEGER NOT NULL DEFAULT 0,
                    edge_count INTEGER NOT NULL DEFAULT 0,
                    status TEXT NOT NULL DEFAULT 'generating',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS kg_node (
                    id TEXT PRIMARY KEY,
                    graph_id TEXT NOT NULL,
                    label TEXT NOT NULL,
                    node_type TEXT NOT NULL DEFAULT 'concept',
                    description TEXT,
                    group_name TEXT,
                    properties TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    FOREIGN KEY (graph_id) REFERENCES kg_graph(id) ON DELETE CASCADE
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS kg_edge (
                    id TEXT PRIMARY KEY,
                    graph_id TEXT NOT NULL,
                    source_id TEXT NOT NULL,
                    target_id TEXT NOT NULL,
                    label TEXT NOT NULL,
                    edge_type TEXT NOT NULL DEFAULT 'related_to',
                    weight REAL NOT NULL DEFAULT 1.0,
                    properties TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    FOREIGN KEY (graph_id) REFERENCES kg_graph(id) ON DELETE CASCADE
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_kg_node_graph ON kg_node(graph_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_kg_edge_graph ON kg_edge(graph_id)");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init graph tables: " + e.getMessage(), e);
        }
    }

    // ===== Graph CRUD =====

    public Graph createGraph(String title, String sourceFile, String description) {
        String id = "graph-" + UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();
        synchronized (WRITE_LOCK) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO kg_graph (id, title, source_file, description, node_count, edge_count, status, created_at, updated_at) " +
                                 "VALUES (?, ?, ?, ?, 0, 0, 'generating', ?, ?)")) {
                ps.setString(1, id);
                ps.setString(2, title);
                ps.setString(3, sourceFile);
                ps.setString(4, description);
                ps.setLong(5, now);
                ps.setLong(6, now);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create graph: " + e.getMessage(), e);
            }
        }
        return new Graph(id, title, sourceFile, description, 0, 0, "generating", now, now);
    }

    public Graph getGraph(String id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM kg_graph WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapGraph(rs) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public List<Graph> listGraphs() {
        List<Graph> result = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM kg_graph ORDER BY updated_at DESC")) {
            while (rs.next()) result.add(mapGraph(rs));
        } catch (SQLException ignored) {}
        return result;
    }

    public void deleteGraph(String id) {
        synchronized (WRITE_LOCK) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement delEdges = conn.prepareStatement("DELETE FROM kg_edge WHERE graph_id = ?");
                 PreparedStatement delNodes = conn.prepareStatement("DELETE FROM kg_node WHERE graph_id = ?");
                 PreparedStatement delGraph = conn.prepareStatement("DELETE FROM kg_graph WHERE id = ?")) {
                delEdges.setString(1, id);
                delEdges.executeUpdate();
                delNodes.setString(1, id);
                delNodes.executeUpdate();
                delGraph.setString(1, id);
                delGraph.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("[WARN] Failed to delete graph: " + e.getMessage());
        }
        }
    }

    public void updateGraphStats(String graphId) {
        synchronized (WRITE_LOCK) {
        try (Connection conn = getConnection()) {
            int nodes = 0, edges = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) as total FROM kg_node WHERE graph_id = ?")) {
                ps.setString(1, graphId);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) nodes = rs.getInt("total"); }
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) as total FROM kg_edge WHERE graph_id = ?")) {
                ps.setString(1, graphId);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) edges = rs.getInt("total"); }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE kg_graph SET node_count = ?, edge_count = ?, status = 'ready', updated_at = ? WHERE id = ?")) {
                ps.setInt(1, nodes);
                ps.setInt(2, edges);
                ps.setLong(3, System.currentTimeMillis());
                ps.setString(4, graphId);
                ps.executeUpdate();
            }
        } catch (SQLException ignored) {}
        }
    }

    public void markGraphFailed(String graphId, String error) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE kg_graph SET status = 'failed', description = ?, updated_at = ? WHERE id = ?")) {
            ps.setString(1, error);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, graphId);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    // ===== Node CRUD =====

    public Node createNode(String graphId, String label, String nodeType, String description, String groupName, Map<String, String> properties) {
        String id = "node-" + UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO kg_node (id, graph_id, label, node_type, description, group_name, properties, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, graphId);
            ps.setString(3, label);
            ps.setString(4, nodeType != null ? nodeType : "concept");
            ps.setString(5, description);
            ps.setString(6, groupName);
            ps.setString(7, serializeMap(properties));
            ps.setLong(8, now);
            ps.setLong(9, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[WARN] Failed to create node: " + e.getMessage());
        }
        return new Node(id, graphId, label, nodeType, description, groupName, properties, now, now);
    }

    public List<Node> listNodesByGraph(String graphId) {
        List<Node> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM kg_node WHERE graph_id = ? ORDER BY created_at ASC")) {
            ps.setString(1, graphId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapNode(rs));
            }
        } catch (SQLException ignored) {}
        return result;
    }

    public Node getNode(String id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM kg_node WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapNode(rs) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public void deleteNode(String id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM kg_node WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    // ===== Edge CRUD =====

    public Edge createEdge(String graphId, String sourceId, String targetId, String label, String edgeType, double weight) {
        String id = "edge-" + UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO kg_edge (id, graph_id, source_id, target_id, label, edge_type, weight, properties, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, graphId);
            ps.setString(3, sourceId);
            ps.setString(4, targetId);
            ps.setString(5, label);
            ps.setString(6, edgeType != null ? edgeType : "related_to");
            ps.setDouble(7, weight > 0 ? weight : 1.0);
            ps.setLong(8, now);
            ps.setLong(9, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[WARN] Failed to create edge: " + e.getMessage());
        }
        return new Edge(id, graphId, sourceId, targetId, label, edgeType, weight, null, now, now);
    }

    public List<Edge> listEdgesByGraph(String graphId) {
        List<Edge> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM kg_edge WHERE graph_id = ? ORDER BY created_at ASC")) {
            ps.setString(1, graphId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapEdge(rs));
            }
        } catch (SQLException ignored) {}
        return result;
    }

    public void deleteEdge(String id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM kg_edge WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    // ===== Bulk Import from LLM Extraction =====

    /**
     * Import a full extraction result as a new graph.
     * Uses a single connection + transaction.
     */
    public Graph importFromExtraction(String title, String sourceFile, ExtractionResult extraction) {
        String graphId = "graph-" + UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();
        synchronized (WRITE_LOCK) {

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Create graph
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO kg_graph (id, title, source_file, description, node_count, edge_count, status, created_at, updated_at) VALUES (?, ?, ?, ?, 0, 0, 'generating', ?, ?)")) {
                    ps.setString(1, graphId);
                    ps.setString(2, title);
                    ps.setString(3, sourceFile);
                    ps.setString(4, extraction.summary());
                    ps.setLong(5, now);
                    ps.setLong(6, now);
                    ps.executeUpdate();
                }

                // Insert nodes
                Map<String, String> idMap = new java.util.HashMap<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO kg_node (id, graph_id, label, node_type, description, group_name, properties, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (var node : extraction.nodes()) {
                        String nodeId = "node-" + UUID.randomUUID().toString().substring(0, 8);
                        ps.setString(1, nodeId);
                        ps.setString(2, graphId);
                        ps.setString(3, node.label());
                        ps.setString(4, node.type() != null ? node.type() : "concept");
                        ps.setString(5, node.description());
                        ps.setString(6, node.group());
                        ps.setString(7, serializeMap(node.properties()));
                        ps.setLong(8, now);
                        ps.setLong(9, now);
                        ps.executeUpdate();
                        if (node.id() != null) idMap.put(node.id(), nodeId);
                    }
                }

                // Insert edges
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO kg_edge (id, graph_id, source_id, target_id, label, edge_type, weight, properties, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)")) {
                    for (var edge : extraction.edges()) {
                        String srcId = edge.source() != null ? idMap.get(edge.source()) : null;
                        String tgtId = edge.target() != null ? idMap.get(edge.target()) : null;
                        if (srcId != null && tgtId != null) {
                            String edgeId = "edge-" + UUID.randomUUID().toString().substring(0, 8);
                            ps.setString(1, edgeId);
                            ps.setString(2, graphId);
                            ps.setString(3, srcId);
                            ps.setString(4, tgtId);
                            ps.setString(5, edge.label());
                            ps.setString(6, edge.type() != null ? edge.type() : "related_to");
                            ps.setDouble(7, edge.weight() > 0 ? edge.weight() : 1.0);
                            ps.setLong(8, now);
                            ps.setLong(9, now);
                            ps.executeUpdate();
                        }
                    }
                }

                // Update stats
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE kg_graph SET node_count = (SELECT COUNT(*) FROM kg_node WHERE graph_id = ?), " +
                                "edge_count = (SELECT COUNT(*) FROM kg_edge WHERE graph_id = ?), " +
                                "status = 'ready', updated_at = ? WHERE id = ?")) {
                    ps.setString(1, graphId);
                    ps.setString(2, graphId);
                    ps.setLong(3, System.currentTimeMillis());
                    ps.setString(4, graphId);
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException("importFromExtraction failed: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("importFromExtraction failed: " + e.getMessage(), e);
        }
        }

        return getGraph(graphId);
    }

    /**
     * Incrementally merge extraction results into an existing graph.
     * Uses a single connection + transaction to avoid SQLITE_BUSY_SNAPSHOT.
     */
    public Graph mergeIntoGraph(String graphId, ExtractionResult extraction, String sourceFile) {
        synchronized (WRITE_LOCK) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Load existing nodes for dedup within this transaction
                Map<String, String> existingKeyMap = new java.util.HashMap<>();
                Map<String, String> idMap = new java.util.HashMap<>();
                java.util.Set<String> existingEdgeKeys = new java.util.HashSet<>();

                try (PreparedStatement ps = conn.prepareStatement("SELECT id, label, node_type FROM kg_node WHERE graph_id = ?")) {
                    ps.setString(1, graphId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String key = (rs.getString("label") + "||" + rs.getString("node_type")).toLowerCase();
                            existingKeyMap.put(key, rs.getString("id"));
                        }
                    }
                }

                // Load existing edges for dedup
                try (PreparedStatement ps = conn.prepareStatement("SELECT source_id, target_id, label FROM kg_edge WHERE graph_id = ?")) {
                    ps.setString(1, graphId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            existingEdgeKeys.add(rs.getString("source_id") + "->" + rs.getString("target_id") + ":" + rs.getString("label"));
                        }
                    }
                }

                long now = System.currentTimeMillis();
                int addedNodes = 0;

                try (PreparedStatement insertNode = conn.prepareStatement(
                        "INSERT INTO kg_node (id, graph_id, label, node_type, description, group_name, properties, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (var node : extraction.nodes()) {
                        String key = (node.label() + "||" + (node.type() != null ? node.type() : "concept")).toLowerCase();
                        String existingId = existingKeyMap.get(key);
                        if (existingId != null) {
                            if (node.id() != null) idMap.put(node.id(), existingId);
                        } else {
                            String nodeId = "node-" + UUID.randomUUID().toString().substring(0, 8);
                            insertNode.setString(1, nodeId);
                            insertNode.setString(2, graphId);
                            insertNode.setString(3, node.label());
                            insertNode.setString(4, node.type() != null ? node.type() : "concept");
                            insertNode.setString(5, node.description());
                            insertNode.setString(6, node.group());
                            insertNode.setString(7, serializeMap(node.properties()));
                            insertNode.setLong(8, now);
                            insertNode.setLong(9, now);
                            insertNode.executeUpdate();
                            if (node.id() != null) idMap.put(node.id(), nodeId);
                            existingKeyMap.put(key, nodeId);
                            addedNodes++;
                        }
                    }
                }

                int addedEdges = 0;

                try (PreparedStatement insertEdge = conn.prepareStatement(
                        "INSERT INTO kg_edge (id, graph_id, source_id, target_id, label, edge_type, weight, properties, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)")) {
                    for (var edge : extraction.edges()) {
                        String srcId = edge.source() != null ? idMap.get(edge.source()) : null;
                        String tgtId = edge.target() != null ? idMap.get(edge.target()) : null;
                        if (srcId != null && tgtId != null) {
                            String edgeKey = srcId + "->" + tgtId + ":" + edge.label();
                            if (!existingEdgeKeys.contains(edgeKey)) {
                                String edgeId = "edge-" + UUID.randomUUID().toString().substring(0, 8);
                                insertEdge.setString(1, edgeId);
                                insertEdge.setString(2, graphId);
                                insertEdge.setString(3, srcId);
                                insertEdge.setString(4, tgtId);
                                insertEdge.setString(5, edge.label());
                                insertEdge.setString(6, edge.type() != null ? edge.type() : "related_to");
                                insertEdge.setDouble(7, edge.weight() > 0 ? edge.weight() : 1.0);
                                insertEdge.setLong(8, now);
                                insertEdge.setLong(9, now);
                                insertEdge.executeUpdate();
                                existingEdgeKeys.add(edgeKey);
                                addedEdges++;
                            }
                        }
                    }
                }

                // Update graph stats
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE kg_graph SET node_count = (SELECT COUNT(*) FROM kg_node WHERE graph_id = ?), " +
                                "edge_count = (SELECT COUNT(*) FROM kg_edge WHERE graph_id = ?), " +
                                "status = 'ready', updated_at = ? WHERE id = ?")) {
                    ps.setString(1, graphId);
                    ps.setString(2, graphId);
                    ps.setLong(3, now);
                    ps.setString(4, graphId);
                    ps.executeUpdate();
                }

                conn.commit();
                System.out.println("[GRAPH] Merged into " + graphId + ": +" + addedNodes + " nodes, +" + addedEdges + " edges (from " + sourceFile + ")");
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("[WARN] mergeIntoGraph failed: " + e.getMessage());
            }
        } catch (SQLException e) {
            System.err.println("[WARN] mergeIntoGraph connection failed: " + e.getMessage());
        }
        }
        return getGraph(graphId);
    }

    /**
     * Find a graph that contains the given source file.
     */
    public Graph findBySourceFile(String sourceFile) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM kg_graph WHERE source_file = ? ORDER BY updated_at DESC LIMIT 1")) {
            ps.setString(1, sourceFile);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapGraph(rs) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    /**
     * Find the main (global) knowledge graph, identified by source_file = '__FULL_KB__'.
     * There is at most one such graph.
     */
    public Graph findMainGraph() {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM kg_graph WHERE source_file = '__FULL_KB__' LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapGraph(rs);
            }
        } catch (SQLException ignored) {}
        return null;
    }

    /**
     * Create the main (global) knowledge graph with source_file = '__FULL_KB__'.
     * If one already exists, return it instead.
     */
    public Graph getOrCreateMainGraph() {
        synchronized (WRITE_LOCK) {
            Graph existing = findMainGraph();
            if (existing != null) return existing;

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO kg_graph (id, title, source_file, description, node_count, edge_count, status, created_at, updated_at) " +
                                 "VALUES (?, ?, '__FULL_KB__', ?, 0, 0, 'generating', ?, ?)")) {
                String id = "graph-main-" + UUID.randomUUID().toString().substring(0, 8);
                long now = System.currentTimeMillis();
                ps.setString(1, id);
                ps.setString(2, "全库知识图谱");
                ps.setString(3, "基于全部知识库文档自动生成并持续更新");
                ps.setLong(4, now);
                ps.setLong(5, now);
                ps.executeUpdate();
                return new Graph(id, "全库知识图谱", "__FULL_KB__",
                        "基于全部知识库文档自动生成并持续更新", 0, 0, "generating", now, now);
            } catch (SQLException e) {
                return findMainGraph();
            }
        }
    }

    // ===== Mapping =====

    private Graph mapGraph(ResultSet rs) throws SQLException {
        return new Graph(
                rs.getString("id"), rs.getString("title"), rs.getString("source_file"),
                rs.getString("description"), rs.getInt("node_count"), rs.getInt("edge_count"),
                rs.getString("status"), rs.getLong("created_at"), rs.getLong("updated_at")
        );
    }

    private Node mapNode(ResultSet rs) throws SQLException {
        return new Node(
                rs.getString("id"), rs.getString("graph_id"), rs.getString("label"),
                rs.getString("node_type"), rs.getString("description"), rs.getString("group_name"),
                deserializeMap(rs.getString("properties")),
                rs.getLong("created_at"), rs.getLong("updated_at")
        );
    }

    private Edge mapEdge(ResultSet rs) throws SQLException {
        return new Edge(
                rs.getString("id"), rs.getString("graph_id"),
                rs.getString("source_id"), rs.getString("target_id"),
                rs.getString("label"), rs.getString("edge_type"),
                rs.getDouble("weight"), deserializeMap(rs.getString("properties")),
                rs.getLong("created_at"), rs.getLong("updated_at")
        );
    }

    private String serializeMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) return null;
        try { return MAPPER.writeValueAsString(map); }
        catch (JsonProcessingException e) { return null; }
    }

    private Map<String, String> deserializeMap(String json) {
        if (json == null || json.isBlank()) return null;
        try { return MAPPER.readValue(json, new TypeReference<>() {}); }
        catch (JsonProcessingException e) { return null; }
    }

    // ===== Records =====

    public record Graph(String id, String title, String sourceFile, String description,
                        int nodeCount, int edgeCount, String status,
                        long createdAt, long updatedAt) {}

    public record Node(String id, String graphId, String label, String nodeType,
                       String description, String groupName, Map<String, String> properties,
                       long createdAt, long updatedAt) {}

    public record Edge(String id, String graphId, String sourceId, String targetId,
                       String label, String edgeType, double weight, Map<String, String> properties,
                       long createdAt, long updatedAt) {}

    public record ExtractedNode(String id, String label, String type,
                                String description, String group,
                                Map<String, String> properties) {}

    public record ExtractedEdge(String source, String target, String label,
                                String type, double weight) {}

    public record ExtractionResult(List<ExtractedNode> nodes, List<ExtractedEdge> edges,
                                   String summary) {}
}
