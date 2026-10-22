package io.jenkins.plugins.buildvalue.preview;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.jenkins.plugins.buildvalue.logic.MachineMetrics;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Serves the preview and a one-second snapshot of this computer. */
public final class MachineFeed {
    private MachineFeed() {}

    public static void main(String[] args) throws Exception {
        MachineMetrics.start();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8765), 0);
        server.createContext("/machine", MachineFeed::machine);
        server.createContext("/activity", MachineFeed::activity);
        server.createContext("/", MachineFeed::page);
        server.start();
        System.out.println("open http://127.0.0.1:8765/");
        Thread.sleep(Long.MAX_VALUE);
    }

    private static void machine(HttpExchange exchange) throws IOException {
        MachineMetrics.pace(query(exchange, "pace"));
        write(exchange, "application/json;charset=UTF-8", MachineMetrics.json().getBytes(StandardCharsets.UTF_8));
    }

    private static void activity(HttpExchange exchange) throws IOException {
        write(exchange, "application/json;charset=UTF-8", activityJson().getBytes(StandardCharsets.UTF_8));
    }

    private static String activityJson() {
        long newestEnd = -1;
        String job = "";
        int number = 0;
        Path file = Path.of("preview/out/samples.tsv");
        if (Files.isRegularFile(file)) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (!line.startsWith("B\t")) {
                        continue;
                    }
                    String[] part = line.split("\t", -1);
                    if (part.length < 6) {
                        continue;
                    }
                    long start = Long.parseLong(part[4]);
                    long duration = Long.parseLong(part[5]);
                    long end = start + Math.max(0, duration);
                    if (end >= newestEnd) {
                        newestEnd = end;
                        job = part[1];
                        number = Integer.parseInt(part[2]);
                    }
                }
            } catch (Exception ignored) {
                newestEnd = -1;
            }
        }
        StringBuilder json = new StringBuilder();
        json.append("{\"ok\":true,\"preview\":true,\"users\":[],\"builds\":[],\"downloads\":[]");
        json.append(",\"lastRun\":{");
        if (newestEnd < 0) {
            json.append("\"ago\":\"\"");
        } else {
            long ago = Math.max(0, System.currentTimeMillis() - newestEnd);
            json.append("\"job\":").append(quote(job))
                    .append(",\"number\":").append(number)
                    .append(",\"building\":false,\"ago\":").append(quote(agoText(ago)));
        }
        json.append("}}");
        return json.toString();
    }

    private static String agoText(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long days = seconds / 86_400;
        long hours = (seconds % 86_400) / 3_600;
        long minutes = (seconds % 3_600) / 60;
        if (days > 0) {
            return hours == 0 ? days + " 天" : days + " 天 " + hours + " 小时";
        }
        if (hours > 0) {
            return minutes == 0 ? hours + " 小时" : hours + " 小时 " + minutes + " 分";
        }
        return Math.max(1, minutes) + " 分";
    }

    private static String quote(String raw) {
        if (raw == null) {
            return "\"\"";
        }
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void page(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path != null && path.startsWith("/plugin/build-stats/images/")) {
            String name = path.substring("/plugin/build-stats/images/".length());
            if (name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.contains("..")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            Path image = Path.of("src/main/webapp/images").resolve(name);
            if (!Files.isRegularFile(image)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            String type = name.endsWith(".png") ? "image/png" : "image/jpeg";
            write(exchange, type, Files.readAllBytes(image));
            return;
        }
        Path file = Path.of("preview/index.html");
        if (!Files.isRegularFile(file)) {
            write(exchange, "text/plain;charset=UTF-8", "preview missing".getBytes(StandardCharsets.UTF_8));
            return;
        }
        write(exchange, "text/html;charset=UTF-8", Files.readAllBytes(file));
    }

    private static String query(HttpExchange exchange, String key) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null) {
            return "";
        }
        for (String part : raw.split("&")) {
            int cut = part.indexOf('=');
            if (cut > 0 && key.equals(part.substring(0, cut))) {
                return part.substring(cut + 1);
            }
        }
        return "";
    }

    private static void write(HttpExchange exchange, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
