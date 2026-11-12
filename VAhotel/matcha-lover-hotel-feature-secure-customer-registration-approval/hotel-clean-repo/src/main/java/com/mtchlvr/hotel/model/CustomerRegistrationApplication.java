package com.mtchlvr.hotel.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Stores an unapproved customer application separately from active login accounts.
 * Uploaded identity documents are referenced by private filesystem paths, never public URLs.
 */
@Entity
@Table(name = "customer_registration_applications")
public class CustomerRegistrationApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, unique=true, length=160)
    private String email;
    @Column(nullable=false, length=120)
    private String firstName;
    @Column(nullable=false, length=120)
    private String lastName;
    @Column(nullable=false, length=100)
    private String passwordHash;

    @Column(nullable=false, length=100)
    private String otpHash;
    @Column(nullable=false)
    private Instant otpExpiresAt;
    @Column(nullable=false)
    private Instant otpLastSentAt;
    @Column(nullable=false)
    private int otpAttempts;
    @Column(nullable=false)
    private boolean emailVerified;

    @Column(nullable=false, length=40)
    private String idType;
    @Column(nullable=false, length=500)
    private String frontDocumentPath;
    @Column(nullable=false, length=500)
    private String backDocumentPath;

    @Column(nullable=false)
    private Instant termsAcceptedAt;
    @Column(nullable=false, length=30)
    private String status = "PENDING_APPROVAL";
    @Column(length=1000)
    private String reviewNote;
    private Instant reviewedAt;
    private String reviewedBy;
    @Column(nullable=false)
    private Instant createdAt = Instant.now();

    protected CustomerRegistrationApplication() {}

    public CustomerRegistrationApplication(String email, String firstName, String lastName,
            String passwordHash, String otpHash, Instant otpExpiresAt, Instant otpLastSentAt,
            String idType, String frontDocumentPath, String backDocumentPath, Instant termsAcceptedAt) {
        this.email=email; this.firstName=firstName; this.lastName=lastName;
        this.passwordHash=passwordHash; this.otpHash=otpHash;
        this.otpExpiresAt=otpExpiresAt; this.otpLastSentAt=otpLastSentAt;
        this.idType=idType; this.frontDocumentPath=frontDocumentPath;
        this.backDocumentPath=backDocumentPath; this.termsAcceptedAt=termsAcceptedAt;
        this.emailVerified=true;
    }

    public Long getId(){return id;}
    public String getEmail(){return email;}
    public String getFirstName(){return firstName;}
    public String getLastName(){return lastName;}
    public String getPasswordHash(){return passwordHash;}
    public String getIdType(){return idType;}
    public String getFrontDocumentPath(){return frontDocumentPath;}
    public String getBackDocumentPath(){return backDocumentPath;}
    public String getStatus(){return status;}
    public Instant getCreatedAt(){return createdAt;}
    public String getReviewNote(){return reviewNote;}
    public String getReviewedBy(){return reviewedBy;}
    public Instant getReviewedAt(){return reviewedAt;}
    public void decide(String decision,String note,String admin){
        this.status=decision; this.reviewNote=note; this.reviewedBy=admin; this.reviewedAt=Instant.now();
    }
}
