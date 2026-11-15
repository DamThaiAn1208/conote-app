package com.conote.server.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.Map;

public class EntityManagerUtil {
    private static final String PERSISTENCE_UNIT_NAME = "conotePU";
    private static final EntityManagerFactory emf = buildEntityManagerFactory();

    private static EntityManagerFactory buildEntityManagerFactory() {
        String url = requiredEnvironment("CONOTE_DB_URL");
        String user = requiredEnvironment("CONOTE_DB_USER");
        String password = System.getenv("CONOTE_DB_PASSWORD");
        if (password == null) {
            throw new IllegalStateException("Set CONOTE_DB_PASSWORD (an empty value is allowed only for local development)");
        }
        try {
            return Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME, Map.of(
                    "jakarta.persistence.jdbc.driver", "org.mariadb.jdbc.Driver",
                    "jakarta.persistence.jdbc.url", url,
                    "jakarta.persistence.jdbc.user", user,
                    "jakarta.persistence.jdbc.password", password));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create EntityManagerFactory", e);
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Set " + name + " before using MariaDB");
        }
        return value;
    }

    public static EntityManager getEntityManager() {
        return emf.createEntityManager();
    }

    public static void close() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }
}
