# Matcha Lover Hotel

Hotel reservations and management app built with Java 21, Spring Boot, Maven, SQLite, Thymeleaf, HTML, CSS, and vanilla JavaScript.

## Run locally

Requirements: JDK 21 and Maven 3.9 or newer.

From this project directory:

```bash
mvn spring-boot:run
```

Open <http://localhost:8080>. SQLite data is stored at `./data/mtchlvr.db`; government ID images and room photos are stored outside the public static directory. The database uses additive schema updates and is not reset on startup.

To run the tests:

```bash
mvn test
```

## Features

- Customer applications with email OTP verification, private government ID uploads, consent, and Super Admin approval/rejection.
- Customer profile, customer code, reservation history, booking cancellation, early checkout and extension requests, status tracking, and printable receipts.
- Room inventory CRUD, descriptions, pricing, availability schedules, and five-image uploads with an automatic card carousel.
- Fixed 2:00 PM check-in and 12:00 PM checkout for new stays.
- Online and walk-in reservations, server-side overlap checks, manager review, checkout, reservation history, and receipts.
- Super Admin manager account CRUD and configurable manager roles.
- Daily, weekly, monthly, and annual revenue summaries based on completed stays and real, non-simulated paid transactions.
- Guest reports, refunds, and the optional hotel chatbot.

## Environment and first account setup

Never commit secrets. In GitHub, add them under **Settings → Secrets and variables → Codespaces** (or use the ignored `local-secrets.properties` file for local development). A Codespace must be restarted/rebuilt to receive changed Codespaces secrets.

Required for customer registration and sign-in CAPTCHA:

- `RECAPTCHA_SITE_KEY` — Google reCAPTCHA v2 checkbox site key.
- `RECAPTCHA_SECRET_KEY` — matching private secret key.
- Add the exact site hostname to the allowed domains for that same reCAPTCHA key. For local use, allow `localhost`.

Required to send customer email OTPs:

- `SPRING_MAIL_USERNAME` — SMTP account.
- `SPRING_MAIL_PASSWORD` — SMTP password or app password.

The first Super Admin and optional initial manager are provisioned only when their account details are configured on a fresh database. They are never assigned default passwords, and existing accounts are never reset:

- `INITIAL_SUPER_ADMIN_EMAIL`, `INITIAL_SUPER_ADMIN_PASSWORD`, optional `INITIAL_SUPER_ADMIN_NAME`.
- `INITIAL_MANAGER_EMAIL`, `INITIAL_MANAGER_PASSWORD`, optional `INITIAL_MANAGER_NAME`, and `INITIAL_MANAGER_ROLE` (`MANAGER`, `ONLINE_MANAGER`, or `ONSITE_MANAGER`).

Passwords use the configured policy below. To adjust it, set `REGISTRATION_PASSWORD_MIN_LENGTH`, `REGISTRATION_PASSWORD_MAX_LENGTH`, `REGISTRATION_PASSWORD_REQUIRE_LOWERCASE`, `REGISTRATION_PASSWORD_REQUIRE_UPPERCASE`, `REGISTRATION_PASSWORD_REQUIRE_NUMBER`, `REGISTRATION_PASSWORD_REQUIRE_SPECIAL`, and `REGISTRATION_PASSWORD_ALLOWED_SPECIALS`. Defaults require 10–70 characters, mixed case, a number, a listed special character, and no whitespace.

Optional integrations and storage paths:

- `GROQ_API_KEY` — hotel chatbot.
- `VA_IDENTITY_UPLOAD_DIR` — private customer ID images (default `./private-uploads/identity`).
- `VA_ROOM_PHOTO_DIR` — room photos (default `./uploads/rooms`).

For local-only reCAPTCHA values, copy `local-secrets.example.properties` to `local-secrets.properties`, replace the values, then restart the app. `local-secrets.properties` is ignored by Git.

## Payment reporting

NexusPay records are simulations, not real gateway transactions. Revenue reports exclude simulated payments and pending reservations. No simulated payment is represented as actual revenue.

## Packaging and privacy

Do not publish real applicant ID documents, local database files, local secrets, or upload directories. The ZIP package is source-only and excludes those files and Maven build output. See `PHOTO-SOURCES.md` for image credits and source notes.
