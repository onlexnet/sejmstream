package com.example.funsejmapi;

import java.util.List;
import java.util.Optional;

/**
 * Resolves the Facebook token from the injected environment.
 */
final class FacebookTokenProvider {

    private static final List<String> ENVIRONMENT_VARIABLE_CANDIDATES = List.of("FB_TOKEN");

    String resolveToken() {
        return resolveFromEnvironment().orElse(null);
    }

    private Optional<String> resolveFromEnvironment() {
        return ENVIRONMENT_VARIABLE_CANDIDATES.stream()
                .map(System.getenv()::get)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

}
