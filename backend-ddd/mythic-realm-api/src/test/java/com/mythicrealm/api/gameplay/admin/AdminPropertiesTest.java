package com.mythicrealm.api.gameplay.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AdminPropertiesTest {

    @Test
    void parsesConfiguredAdminUsernames() {
        AdminProperties properties = new AdminProperties("123456, gm ,owner");

        assertTrue(properties.isAdminUsername("123456"));
        assertTrue(properties.isAdminUsername("gm"));
        assertTrue(properties.isAdminUsername("owner"));
        assertFalse(properties.isAdminUsername("player"));
        assertFalse(properties.isAdminUsername(null));
    }
}
