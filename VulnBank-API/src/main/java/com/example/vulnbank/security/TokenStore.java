package com.example.vulnbank.security;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class TokenStore {
    private final ConcurrentMap<String, Long> userByToken = new ConcurrentHashMap<>();

    public String issue(Long userId) {
        String token = UUID.randomUUID().toString();
        userByToken.put(token, userId);
        return token;
    }

    public Optional<Long> findUserId(String token) {
        return Optional.ofNullable(userByToken.get(token));
    }
}
