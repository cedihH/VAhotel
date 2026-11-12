package com.mtchlvr.hotel.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Stores guest information for reservations created directly
 * by the On-Site Manager.
 *
 * A walk-in guest does not need an AppUser account or login credentials.
 */
@Entity
@Table(name = "walk_in_guests")
public class WalkInGuest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 160)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected WalkInGuest() {
    }

    public WalkInGuest(String fullName, String email, String phone) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setFullName(String value) {
        fullName = value;
    }

    public void setEmail(String value) {
        email = value;
    }

    public void setPhone(String value) {
        phone = value;
    }
}
