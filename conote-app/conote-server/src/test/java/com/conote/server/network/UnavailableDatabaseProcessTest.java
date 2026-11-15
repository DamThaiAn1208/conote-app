package com.conote.server.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** A failing DB must yield a protocol error without taking down the dispatcher. */
class UnavailableDatabaseProcessTest {
    @Test
    void unreachableMariaDbReturnsUnavailable() throws Exception {
        int unusedPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            unusedPort = probe.getLocalPort();
        }
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        ProcessBuilder processBuilder = new ProcessBuilder(java.toString(), "-cp",
                System.getProperty("java.class.path"), UnavailableDatabaseProbe.class.getName());
        processBuilder.environment().put("CONOTE_DB_URL",
                "jdbc:mariadb://127.0.0.1:" + unusedPort
                        + "/conote_unavailable_probe?connectTimeout=1000&socketTimeout=1000");
        processBuilder.environment().put("CONOTE_DB_USER", "probe");
        processBuilder.environment().put("CONOTE_DB_PASSWORD", "probe");
        Path log = Files.createTempFile("conote-db-unavailable-", ".log");
        try {
            processBuilder.redirectErrorStream(true).redirectOutput(log.toFile());
            Process process = processBuilder.start();
            try {
                if (!process.waitFor(60, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                    throw new AssertionError("DB probe process timed out: " + Files.readString(log));
                }
                String output = Files.readString(log);
                assertEquals(0, process.exitValue(), output);
                assertTrue(output.contains("DB_UNAVAILABLE_OK"), output);
            } finally {
                if (process.isAlive()) process.destroyForcibly();
            }
        } finally {
            Files.deleteIfExists(log);
        }
    }
}
