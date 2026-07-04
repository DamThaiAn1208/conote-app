package com.conote.server.dao;

import com.conote.common.model.User;
import com.conote.server.config.EntityManagerUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;

import java.util.Optional;

public class UserDao {

    public User save(User user) {
        EntityManager entityManager = EntityManagerUtil.getEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();

        try {
            transaction.begin();
            entityManager.persist(user);
            transaction.commit();
            return user;
        } catch (Exception exception) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw exception;
        } finally {
            entityManager.close();
        }
    }

    public Optional<User> findByEmail(String email) {
        EntityManager entityManager = EntityManagerUtil.getEntityManager();

        try {
            User user = entityManager
                    .createQuery(
                            "SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)",
                            User.class
                    )
                    .setParameter("email", email)
                    .getSingleResult();

            return Optional.of(user);
        } catch (NoResultException exception) {
            return Optional.empty();
        } finally {
            entityManager.close();
        }
    }

    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    public Optional<User> findById(Long userId) {
        EntityManager entityManager = EntityManagerUtil.getEntityManager();

        try {
            User user = entityManager.find(User.class, userId);
            return Optional.ofNullable(user);
        } finally {
            entityManager.close();
        }
    }
}