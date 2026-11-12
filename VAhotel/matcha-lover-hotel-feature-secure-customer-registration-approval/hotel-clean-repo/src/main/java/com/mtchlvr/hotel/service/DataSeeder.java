package com.mtchlvr.hotel.service;

import com.mtchlvr.hotel.model.*;
import com.mtchlvr.hotel.repo.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import java.math.BigDecimal;
import java.util.Locale;

@Configuration
public class DataSeeder {
    @Bean CommandLineRunner seed(RoomRepository rooms, UserRepository users, PasswordEncoder encoder, PasswordPolicy passwordPolicy,
            @Value("${hotel.initial-super-admin.email:}") String superAdminEmail,
            @Value("${hotel.initial-super-admin.password:}") String superAdminPassword,
            @Value("${hotel.initial-super-admin.name:Super Admin}") String superAdminName,
            @Value("${hotel.initial-manager.email:}") String managerEmail,
            @Value("${hotel.initial-manager.password:}") String managerPassword,
            @Value("${hotel.initial-manager.name:Hotel Manager}") String managerName,
            @Value("${hotel.initial-manager.role:MANAGER}") String managerRole,
            @Value("${hotel.portal-accounts.customer.password:}") String customerPassword,
            @Value("${hotel.portal-accounts.onsite.password:}") String onsitePassword,
            @Value("${hotel.portal-accounts.online.password:}") String onlinePassword,
            @Value("${hotel.portal-accounts.admin.password:}") String adminPassword) {
        return args -> {
            // Seed each missing room independently so a partially populated database is repaired.
            addType(rooms, "Standard", "1", new BigDecimal("1800"), 2);
            addType(rooms, "Deluxe", "2", new BigDecimal("2600"), 2);
            addType(rooms, "Suite", "3", new BigDecimal("4200"), 2);
            addType(rooms, "Family", "4", new BigDecimal("3500"), 4);
            provisionAccount(users, encoder, passwordPolicy, superAdminEmail, superAdminPassword, superAdminName, "SUPER_ADMIN");
            String normalizedManagerRole = managerRole == null ? "" : managerRole.trim().toUpperCase(Locale.ROOT);
            if (!java.util.Set.of("MANAGER", "ONLINE_MANAGER", "ONSITE_MANAGER").contains(normalizedManagerRole)) {
                throw new IllegalStateException("hotel.initial-manager.role must be MANAGER, ONLINE_MANAGER, or ONSITE_MANAGER.");
            }
            provisionAccount(users, encoder, passwordPolicy, managerEmail, managerPassword, managerName, normalizedManagerRole);
            provisionPortalAccount(users, encoder, passwordPolicy, customerPassword, "cedihCustomer", "Customer Portal", "CUSTOMER");
            provisionPortalAccount(users, encoder, passwordPolicy, onsitePassword, "cedihOnsite", "On-Site Manager", "ONSITE_MANAGER");
            provisionPortalAccount(users, encoder, passwordPolicy, onlinePassword, "cedihOnline", "Online Manager", "ONLINE_MANAGER");
            provisionPortalAccount(users, encoder, passwordPolicy, adminPassword, "cedihAdmin", "Super Admin Portal", "SUPER_ADMIN");
        };
    }

    private void provisionPortalAccount(UserRepository users, PasswordEncoder encoder, PasswordPolicy policy,
            String password, String username, String name, String role) {
        if (password == null || password.isBlank()) return;
        provisionAccount(users, encoder, policy, username, password, name, role);
    }

    private void provisionAccount(UserRepository users, PasswordEncoder encoder, PasswordPolicy policy,
            String configuredEmail, String configuredPassword, String configuredName, String role) {
        String email = configuredEmail == null ? "" : configuredEmail.trim().toLowerCase(Locale.ROOT);
        String password = configuredPassword == null ? "" : configuredPassword;
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.isBlank() || email.chars().anyMatch(Character::isWhitespace)
                || (email.contains("@") && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
            throw new IllegalStateException("A valid login name and password are required to bootstrap " + role + ".");
        }
        if (!policy.isValid(password)) {
            throw new IllegalStateException("The bootstrap password for " + role + " does not meet the configured password policy.");
        }
        AppUser existing = users.findByEmailIgnoreCase(email).orElse(null);
        if (existing != null) {
            if (!role.equalsIgnoreCase(existing.getRole())) {
                throw new IllegalStateException("The configured bootstrap email already belongs to a different role.");
            }
            return;
        }
        String fullName = configuredName == null || configuredName.isBlank() ? role : configuredName.trim();
        users.save(new AppUser(email, encoder.encode(password), fullName, role, true));
    }
    private void addType(RoomRepository rooms, String type, String prefix, BigDecimal rate, int capacity) {
        for (int i = 1; i <= 3; i++) {
            String number = prefix + "0" + i;
            if (!rooms.existsByRoomNumber(number)) {
                rooms.save(new Room(number, type, rate, capacity));
            }
        }
    }
}
