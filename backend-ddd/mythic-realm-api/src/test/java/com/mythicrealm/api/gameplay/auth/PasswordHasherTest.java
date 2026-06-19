package com.mythicrealm.api.gameplay.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void matchesCorrectPassword() {
        String encoded = hasher.hash("correct horse");
        assertTrue(hasher.matches("correct horse", encoded));
    }

    @Test
    void rejectsWrongPassword() {
        String encoded = hasher.hash("correct horse");
        assertFalse(hasher.matches("wrong horse", encoded));
    }

    @Test
    void producesDifferentHashesForSameInput() {
        // random salt => two hashes of the same password must differ, but both verify
        String a = hasher.hash("same");
        String b = hasher.hash("same");
        assertNotEquals(a, b);
        assertTrue(hasher.matches("same", a));
        assertTrue(hasher.matches("same", b));
    }

    @Test
    void encodedFormatIsPbkdf2WithIterations() {
        String[] parts = hasher.hash("x").split("\\$");
        assertTrue(parts.length == 4);
        assertTrue("pbkdf2".equals(parts[0]));
        assertTrue(Integer.parseInt(parts[1]) >= 100_000);
    }

    @Test
    void malformedHashDoesNotMatch() {
        assertFalse(hasher.matches("x", "not-a-valid-hash"));
        assertFalse(hasher.matches("x", "plain$1$salt"));
    }
}
