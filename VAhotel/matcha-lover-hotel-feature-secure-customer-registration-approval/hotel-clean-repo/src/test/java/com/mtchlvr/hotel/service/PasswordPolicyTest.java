package com.mtchlvr.hotel.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {
    private final PasswordPolicy policy = new PasswordPolicy(10, 20, true, true, true, true, "!@#");

    @Test void acceptsConfiguredStrongPasswordWithoutChangingItsCase() {
        assertTrue(policy.isValid("MatchaStay9!"));
        assertFalse(policy.isValid("matchastay9!"));
    }

    @Test void rejectsUnapprovedSpecialsWhitespaceAndLengths() {
        assertFalse(policy.isValid("MatchaStay9$"));
        assertFalse(policy.isValid("Matcha Stay9!"));
        assertFalse(policy.isValid("M9!short"));
        assertFalse(policy.isValid("MatchaStay999999999999!"));
    }

    @Test void supportsOptionalRequirementsWhenConfigured() {
        PasswordPolicy flexible = new PasswordPolicy(8, 32, false, false, false, false, "_");
        assertTrue(flexible.isValid("lettersOnly"));
        assertFalse(flexible.isValid("letters only"));
        assertFalse(flexible.isValid("emoji🙂password"));
    }
}
