package com.example.minipostgres.server;

import com.example.minipostgres.engine.*;
import com.example.minipostgres.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public final class MiniPostgresHttpServer {
    private final MiniPostgresDatabase db;
    private final HttpServer server;

    public MiniPostgresHttpServer(MiniPostgresDatabase db, int port) throws IOException {
        this.db = db;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", this::health);
        server.createContext("/sql", this::sql);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    private void health(HttpExchange ex) throws IOException {
        String body = "{\n  \"status\": \"UP\",\n  \"service\": \"mini-postgres\",\n  \"tables\": " + db.catalog().tableNames().size() + "\n}";
        respond(ex, 200, body);
    }

    private void sql(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
            respond(ex, 405, "{\"error\":\"Use POST\"}");
            return;
        }
        String request = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        QueryResult result = db.execute(new Session(), request);
        respond(ex, result.success() ? 200 : 400, JsonUtil.result(result));
    }

    private void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}
