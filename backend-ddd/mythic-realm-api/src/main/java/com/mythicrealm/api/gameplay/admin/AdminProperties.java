package com.mythicrealm.api.gameplay.admin;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AdminProperties {
    private final Set<String> usernames;

    public AdminProperties(@Value("${mythic.admin.usernames:}") String configuredUsernames) {
        this.usernames = Arrays.stream(configuredUsernames.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdminUsername(String username) {
        return username != null && usernames.contains(username);
    }
}
