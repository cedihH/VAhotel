package com.mtchlvr.hotel.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Shared password rules for customer registration and privileged account provisioning. */
@Component
public class PasswordPolicy {
    private final int minimumLength;
    private final int maximumLength;
    private final boolean requireLowercase;
    private final boolean requireUppercase;
    private final boolean requireNumber;
    private final boolean requireSpecial;
    private final String allowedSpecials;

    public PasswordPolicy(
            @Value("${registration.password.min-length:10}") int minimumLength,
            @Value("${registration.password.max-length:70}") int maximumLength,
            @Value("${registration.password.require-lowercase:true}") boolean requireLowercase,
            @Value("${registration.password.require-uppercase:true}") boolean requireUppercase,
            @Value("${registration.password.require-number:true}") boolean requireNumber,
            @Value("${registration.password.require-special:true}") boolean requireSpecial,
            @Value("${registration.password.allowed-specials:!@#$%^&*()-_+[]:;,.?}") String allowedSpecials) {
        if (minimumLength < 1 || maximumLength < minimumLength) {
            throw new IllegalArgumentException("Password length configuration is invalid.");
        }
        this.minimumLength = minimumLength;
        this.maximumLength = maximumLength;
        this.requireLowercase = requireLowercase;
        this.requireUppercase = requireUppercase;
        this.requireNumber = requireNumber;
        this.requireSpecial = requireSpecial;
        this.allowedSpecials = allowedSpecials == null ? "" : allowedSpecials;
    }

    public boolean isValid(String password) {
        if (password == null || password.length() < minimumLength || password.length() > maximumLength) return false;
        boolean lower = false, upper = false, number = false, special = false;
        for (int offset = 0; offset < password.length();) {
            int cp = password.codePointAt(offset);
            offset += Character.charCount(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) return false;
            if (Character.isLowerCase(cp)) lower = true;
            else if (Character.isUpperCase(cp)) upper = true;
            else if (Character.isDigit(cp)) number = true;
            else {
                if (cp > Character.MAX_VALUE || allowedSpecials.indexOf((char) cp) < 0) return false;
                special = true;
            }
        }
        return (!requireLowercase || lower) && (!requireUppercase || upper)
                && (!requireNumber || number) && (!requireSpecial || special);
    }

    public int getMinimumLength() { return minimumLength; }
    public int getMaximumLength() { return maximumLength; }
    public boolean isRequireLowercase() { return requireLowercase; }
    public boolean isRequireUppercase() { return requireUppercase; }
    public boolean isRequireNumber() { return requireNumber; }
    public boolean isRequireSpecial() { return requireSpecial; }
    public String getAllowedSpecials() { return allowedSpecials; }
}
