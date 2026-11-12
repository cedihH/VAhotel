package com.mtchlvr.hotel.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "guest_reports")
public class GuestReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private AppUser customer;
    @Column(nullable = false)
    private String category;
    @Column(nullable = false, length = 2000)
    private String message;
    @Column(nullable = false)
    private String status = "OPEN";
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    @Column(length = 10000)
    private String conversation = "";
    @Column
    private Boolean visible = true;

    protected GuestReport() {}
    public GuestReport(AppUser customer, String category, String message) {
        this.customer = customer;
        this.category = category;
        this.message = message;
        this.conversation = "GUEST: " + message;
    }
    public Long getId() { return id; }
    public AppUser getCustomer() { return customer; }
    public String getCategory() { return category; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public String getConversation() { return conversation == null ? "" : conversation; }
    public boolean isVisible() { return visible == null || visible; }
    public void setVisible(boolean value) { visible = value; }
    // Check the database column limit before saving another conversation entry.
    public boolean canAppendConversationMessage(String speaker, String message) {
        String entry = speaker + ": " + message;
        String updated = getConversation().isBlank() ? entry : getConversation() + "\n\n" + entry;
        return updated.length() <= 10000;
    }

    public void appendConversationMessage(String speaker, String message) {
        if (!canAppendConversationMessage(speaker, message)) {
            throw new IllegalArgumentException("This report conversation has reached its maximum length. Please submit a new report.");
        }
        String entry = speaker + ": " + message;
        this.conversation = getConversation().isBlank() ? entry : getConversation() + "\n\n" + entry;
    }
    public void setStatus(String status) { this.status = status; }
}