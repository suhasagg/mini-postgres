package com.example.minipostgres;

import com.example.minipostgres.engine.*;
import com.example.minipostgres.server.MiniPostgresHttpServer;

import java.nio.file.Path;
import java.util.Scanner;

public final class Main {
    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "cli";
        Path dataDir = Path.of(System.getenv().getOrDefault("DATA_DIR", "data"));
        try (MiniPostgresDatabase db = new MiniPostgresDatabase(dataDir)) {
            if (mode.equalsIgnoreCase("server")) {
                int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
                MiniPostgresHttpServer server = new MiniPostgresHttpServer(db, port);
                server.start();
                System.out.println("Mini Postgres HTTP server listening on http://localhost:" + port);
                Thread.currentThread().join();
            } else {
                runCli(db);
            }
        }
    }

    private static void runCli(MiniPostgresDatabase db) {
        System.out.println("Mini Postgres Java shell. Type .exit to quit.");
        Session session = new Session();
        Scanner scanner = new Scanner(System.in);
        StringBuilder sql = new StringBuilder();
        while (true) {
            System.out.print(sql.isEmpty() ? "minipg=> " : "     -> ");
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine();
            if (line.trim().equalsIgnoreCase(".exit")) break;
            sql.append(line).append('\n');
            if (!line.trim().endsWith(";")) continue;
            QueryResult result = db.execute(session, sql.toString());
            System.out.println(result.toPrettyTable());
            sql.setLength(0);
        }
    }
}
