package com.mtchlvr.hotel.web;

import com.mtchlvr.hotel.model.*;
import com.mtchlvr.hotel.repo.*;
import com.mtchlvr.hotel.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import jakarta.servlet.http.HttpSession;

@Controller
public class HotelController {
    private final RoomRepository rooms;
    private final UserRepository users;
    private final ReservationRepository reservations;
    private final PaymentRepository payments;
    private final ReservationService reservationService;
    private final PaymentService paymentService;
    private final PencilBookingService pencilBookingService;
    private final CaptchaService captcha;
    private final PasswordEncoder encoder;
    private final ReportRepository reports;
    private final RevenueReportService revenueReportService;
    private final RoomPhotoService roomPhotoService;
    private final PasswordPolicy passwordPolicy;

    public HotelController(RoomRepository rooms, UserRepository users, ReservationRepository reservations,
                           PaymentRepository payments,
                           ReservationService reservationService, PaymentService paymentService,
                           PencilBookingService pencilBookingService,
                           CaptchaService captcha, PasswordEncoder encoder, ReportRepository reports,
                           RevenueReportService revenueReportService, RoomPhotoService roomPhotoService, PasswordPolicy passwordPolicy) {
        this.rooms = rooms;
        this.users = users;
        this.reservations = reservations;
        this.payments = payments;
        this.reservationService = reservationService;
        this.paymentService = paymentService;
        this.pencilBookingService = pencilBookingService;
        this.captcha = captcha;
        this.encoder = encoder;
        this.reports = reports;
        this.revenueReportService = revenueReportService;
        this.roomPhotoService = roomPhotoService;
        this.passwordPolicy = passwordPolicy;
    }

    /**
     * The portal launcher is the first page visitors see.
     * Room browsing remains available after entering the Customer Portal.
     */
    @GetMapping("/")
    public String home() {
        return "portal";
    }
    /** Customer-facing main page opened from the Customer Portal launcher. */
    @GetMapping("/customer-portal")
    public String customerPortalHome() {
        return "index";
    }

    @GetMapping("/rooms") public String browse(Model model) {
        // Customers should only see rooms that are both active and marked available.
        model.addAttribute("rooms", rooms.findByActiveTrueAndAvailableTrueOrderByTypeAscRoomNumberAsc());
        return "rooms";
    }
    /**
     * Opens the shared sign-in page and keeps the portal selected by the visitor.
     * Portal selection is checked again by Spring Security after authentication.
     */
    @GetMapping("/login")
    public String login(
            @RequestParam(name = "portal", required = false) String requestedPortal,
            @RequestParam(name = "roleError", required = false) String roleError,
            Model model) {
        String portal = requestedPortal == null ? "" : requestedPortal.trim().toLowerCase(java.util.Locale.ROOT);
        String portalName = "mtchlvr hotel";
        if ("customer".equals(portal)) {
            portalName = "Customer Portal";
        } else if ("online-manager".equals(portal)) {
            portalName = "Online Reservation Manager Portal";
        } else if ("onsite-manager".equals(portal)) {
            portalName = "On-Site Reservation Manager Portal";
        } else if ("super-admin".equals(portal)) {
            portalName = "Super Admin Portal";
        } else {
            portal = "";
        }
        model.addAttribute("portal", portal);
        model.addAttribute("portalName", portalName);
        model.addAttribute("roleError", roleError != null);
        return "login";
    }

    /** Role-protected landing page for the Super Admin portal. */
    @GetMapping("/super-admin")
    public String superAdminPortal() {
        return "super-admin-portal";
    }

    @GetMapping("/customer/reservations") public String myReservations(Authentication auth, Model model) {
        AppUser customer = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        if (!hasValidCustomerCode(customer)) {
            customer.setCustomerCode(generateCustomerCode());
            users.save(customer);
        }
        model.addAttribute("customer", customer);
        model.addAttribute("customerReports", reports.findAllByOrderByCreatedAtDesc().stream()
                .filter(GuestReport::isVisible)
                .filter(report -> report.getCustomer() != null && report.getCustomer().getId().equals(customer.getId()))
                .toList());
        java.util.List<Reservation> customerReservations =
                reservations.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(auth.getName())
                        .stream()
                        .filter(r -> !"CANCELLED".equals(r.getStatus()))
                        .toList();
        model.addAttribute("reservations", customerReservations);

        // Load payment history and calculate the amount paid for each reservation.
        // Only PAID transactions count toward the customer's outstanding balance.
        java.util.Map<Long, java.util.List<Payment>> paymentRecordsByReservation =
                new java.util.HashMap<>();
        java.util.Map<Long, java.math.BigDecimal> paidAmountsByReservation =
                new java.util.HashMap<>();
        java.util.Map<Long, java.math.BigDecimal> remainingBalancesByReservation =
                new java.util.HashMap<>();

        for (Reservation reservation : customerReservations) {
            java.util.List<Payment> paymentRecords =
                    payments.findAllByReservationIdOrderByPaidAtAsc(reservation.getId());

            java.math.BigDecimal paidAmount = paymentRecords.stream()
                    .filter(payment -> "PAID".equals(payment.getStatus()))
                    .map(Payment::getAmount)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

            java.math.BigDecimal remainingBalance =
                    reservation.getTotalCost().subtract(paidAmount);

            // Prevent a negative displayed balance if a reservation has excess payments.
            if (remainingBalance.compareTo(java.math.BigDecimal.ZERO) < 0) {
                remainingBalance = java.math.BigDecimal.ZERO;
            }

            paymentRecordsByReservation.put(reservation.getId(), paymentRecords);
            paidAmountsByReservation.put(reservation.getId(), paidAmount);
            remainingBalancesByReservation.put(reservation.getId(), remainingBalance);
        }

        model.addAttribute("paymentRecordsByReservation", paymentRecordsByReservation);
        model.addAttribute("paidAmountsByReservation", paidAmountsByReservation);
        model.addAttribute("remainingBalancesByReservation", remainingBalancesByReservation);

        java.time.ZoneId hotelZone = java.time.ZoneId.of("Asia/Manila");
        LocalDate today = LocalDate.now(hotelZone);
        java.time.LocalDateTime now =
                java.time.LocalDateTime.now(hotelZone).withNano(0);

        model.addAttribute("checkoutTodayReservations", customerReservations.stream()
                .filter(r -> "APPROVED".equals(r.getStatus()))
                .filter(r -> r.getCheckOut() != null)
                // Keep overdue approved stays visible until the customer checks out.
                .filter(r -> !r.getCheckOut().isAfter(today))
                .toList());

        model.addAttribute("currentlyCheckedInReservationIds", customerReservations.stream()
                .filter(r -> "APPROVED".equals(r.getStatus()))
                .filter(r -> r.getCheckIn() != null && r.getCheckOut() != null)
                .filter(r -> {
                    java.time.LocalTime inTime = r.getCheckInTime() == null ? java.time.LocalTime.of(14, 0) : r.getCheckInTime();
                    java.time.LocalTime outTime = r.getCheckOutTime() == null ? java.time.LocalTime.NOON : r.getCheckOutTime();
                    return !now.isBefore(r.getCheckIn().atTime(inTime)) && now.isBefore(r.getCheckOut().atTime(outTime));
                })
                .map(Reservation::getId)
                .toList());

        model.addAttribute("checkoutEligibleReservationIds", customerReservations.stream()
                .filter(r -> "APPROVED".equals(r.getStatus()))
                .filter(r -> r.getCheckOut() != null)
                .filter(r -> {
                    java.time.LocalTime outTime = r.getCheckOutTime() == null ? java.time.LocalTime.NOON : r.getCheckOutTime();
                    return !now.isBefore(r.getCheckOut().atTime(outTime));
                })
                .map(Reservation::getId)
                .toList());

        return "my-reservations";
    }

    @GetMapping("/customer/profile")
    public String customerProfile(Authentication auth, Model model) {
        model.addAttribute("customer", users.findByEmailIgnoreCase(auth.getName()).orElseThrow());
        model.addAttribute("passwordPolicy", passwordPolicy);
        return "customer-profile";
    }

    @PostMapping("/customer/profile")
    public String updateCustomerProfile(Authentication auth, @RequestParam String fullName,
            @RequestParam(defaultValue = "") String currentPassword, @RequestParam(defaultValue = "") String newPassword,
            RedirectAttributes flash) {
        AppUser customer = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        String clean = fullName == null ? "" : fullName.trim();
        if (clean.isBlank() || clean.length() > 120 || !clean.matches("[\\p{L}\\p{M} .'-]+")) {
            flash.addFlashAttribute("error", "Enter a valid name using letters, spaces, apostrophes, or hyphens.");
            return "redirect:/customer/profile";
        }
        if (!newPassword.isBlank()) {
            if (!encoder.matches(currentPassword, customer.getPasswordHash())) {
                flash.addFlashAttribute("error", "Current password was not correct."); return "redirect:/customer/profile";
            }
            if (!passwordPolicy.isValid(newPassword)) {
                flash.addFlashAttribute("error", "New password does not meet the configured password policy."); return "redirect:/customer/profile";
            }
            customer.setPasswordHash(encoder.encode(newPassword));
        }
        customer.setFullName(clean); users.save(customer);
        flash.addFlashAttribute("success", "Profile updated."); return "redirect:/customer/profile";
    }
    @PostMapping("/customer/reservations")
    public String makeReservation(Authentication auth,
            @RequestParam Long roomId,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            @RequestParam(defaultValue = "14:00") String checkInTime,
            @RequestParam(defaultValue = "12:00") String checkOutTime,
            @RequestParam(defaultValue = "REGULAR") String bookingType,
            RedirectAttributes flash) {

        try {
            LocalDate arrivalDate = LocalDate.parse(checkIn);
            java.time.LocalTime arrivalTime = java.time.LocalTime.of(14, 0);
            LocalDate departureDate = LocalDate.parse(checkOut);
            java.time.LocalTime departureTime = java.time.LocalTime.NOON;

            if ("REGULAR".equalsIgnoreCase(bookingType)) {
                paymentService.createRegularOnlineBooking(
                        auth.getName(), roomId, arrivalDate, arrivalTime,
                        departureDate, departureTime);

                flash.addFlashAttribute(
                        "success",
                        "Full payment recorded. Your reservation request has been submitted for manager approval.");

            } else if ("PENCIL".equalsIgnoreCase(bookingType)) {
                paymentService.createPencilBooking(
                        auth.getName(), roomId, arrivalDate, arrivalTime,
                        departureDate, departureTime);

                flash.addFlashAttribute(
                        "success",
                        "Your 50% Pencil Booking down payment has been recorded. Your booking is awaiting Online Manager review.");

            } else {
                throw new IllegalArgumentException("Invalid reservation payment option.");
            }

        } catch (Exception ex) {
            flash.addFlashAttribute("error", ex.getMessage() == null
                    ? "Could not create reservation or record payment."
                    : ex.getMessage());
        }

        return "redirect:/customer/reservations";
    }
    /**
     * Records the remaining 50% payment for an approved Pencil Booking.
     * PaymentService verifies reservation ownership, booking type, approval,
     * check-in deadline, and whether the balance has already been paid.
     */
    @PostMapping("/customer/reservations/{id}/pay-balance")
    public String payPencilBalance(
            @PathVariable Long id,
            Authentication auth,
            RedirectAttributes flash) {

        try {
            Payment payment = paymentService.payPencilBalance(id, auth.getName());

            flash.addFlashAttribute(
                    "success",
                    "Pencil Booking balance paid successfully. Transaction reference: "
                            + payment.getTransactionReference());

        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute(
                    "error",
                    ex.getMessage() == null
                            ? "Could not process the Pencil Booking balance payment."
                            : ex.getMessage());
        }

        return "redirect:/customer/reservations";
    }

    @PostMapping("/customer/reservations/{id}/cancel") public String cancel(@PathVariable Long id,
            Authentication auth, RedirectAttributes flash) {
        Reservation r = reservations.findById(id).orElse(null);
        if (r == null || r.getCustomer() == null ||
                !r.getCustomer().getEmail().equalsIgnoreCase(auth.getName())) {
            flash.addFlashAttribute("error", "Reservation not found."); return "redirect:/customer/reservations";
        }
        if (!r.getStatus().equals("PENDING") && !r.getStatus().equals("APPROVED")) {
            flash.addFlashAttribute("error", "This reservation cannot be cancelled in its current status.");
        } else {
            r.setStatusBeforeCancellation(r.getStatus());
            r.setStatus("CANCELLATION_REQUESTED");
            reservations.save(r);
            flash.addFlashAttribute("success", "Cancellation request sent to the manager.");
        }
        return "redirect:/customer/reservations";
    }

    @PostMapping("/customer/reservations/{id}/checkout")
    public String checkout(@PathVariable Long id, Authentication auth, RedirectAttributes flash) {
        Reservation r = reservations.findById(id).orElse(null);
        if (r == null || r.getCustomer() == null ||
                !r.getCustomer().getEmail().equalsIgnoreCase(auth.getName())) {
            flash.addFlashAttribute("error", "Reservation not found.");
            return "redirect:/customer/reservations";
        }
        if (!"APPROVED".equals(r.getStatus())) {
            flash.addFlashAttribute("error", "Only an active approved reservation can be checked out.");
            return "redirect:/customer/reservations";
        }
        if (r.getCheckOut() == null) {
            flash.addFlashAttribute("error", "This reservation has no valid checkout date. Please contact the hotel manager.");
            return "redirect:/customer/reservations";
        }
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Manila");
        java.time.LocalDateTime now = java.time.LocalDateTime.now(zone).withNano(0);
        java.time.LocalTime outTime = r.getCheckOutTime() == null ? java.time.LocalTime.NOON : r.getCheckOutTime();
        java.time.LocalDateTime scheduledEnd = r.getCheckOut().atTime(outTime);
        if (now.isBefore(scheduledEnd)) {
            flash.addFlashAttribute("error", "Checkout is available from your scheduled checkout time: " + scheduledEnd.toLocalTime() + ".");
            return "redirect:/customer/reservations";
        }
        r.setStatus("COMPLETED");
        r.setCheckedOutAt(java.time.Instant.now());
        reservations.save(r);
        flash.addFlashAttribute("success", "Checkout completed. Your reservation is now in history.");
        return "redirect:/customer/reservations";
    }

    @PostMapping("/manager/reservations/{id}/checkout")
    public String managerCheckout(@PathVariable Long id, RedirectAttributes flash) {
        Reservation r = reservations.findById(id).orElse(null);
        if (r == null || !"APPROVED".equals(r.getStatus())) {
            flash.addFlashAttribute("error", "Only an approved active reservation can be checked out.");
            return "redirect:/manager";
        }
        if (r.getCheckIn() == null || r.getCheckOut() == null) {
            flash.addFlashAttribute("error", "This reservation has an invalid stay interval.");
            return "redirect:/manager";
        }
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Manila");
        java.time.LocalDateTime now = java.time.LocalDateTime.now(zone).withNano(0);
        java.time.LocalDateTime started = r.getCheckIn().atTime(r.getCheckInTime() == null ? java.time.LocalTime.of(14, 0) : r.getCheckInTime());
        if (now.isBefore(started)) {
            flash.addFlashAttribute("error", "This reservation has not checked in yet.");
            return "redirect:/manager";
        }
        r.setCheckOut(now.toLocalDate()); r.setCheckOutTime(now.toLocalTime());
        r.setCheckedOutAt(java.time.Instant.now()); r.setStatus("COMPLETED");
        reservations.save(r);
        flash.addFlashAttribute("success", "Checkout processed. The final stay charge is available on the receipt.");
        return "redirect:/manager/reservations/" + id + "/receipt";
    }

    @PostMapping("/customer/reservations/{id}/stay-request")
    public String requestStayChange(@PathVariable Long id, Authentication auth, @RequestParam String type,
            @RequestParam(required = false) LocalDate requestedDate, RedirectAttributes flash) {
        Reservation r = reservations.findById(id).orElse(null);
        if (r == null || r.getCustomer() == null || !r.getCustomer().getEmail().equalsIgnoreCase(auth.getName())) {
            flash.addFlashAttribute("error", "Reservation not found."); return "redirect:/customer/reservations";
        }
        if (!"APPROVED".equals(r.getStatus()) || r.getStayRequestStatus() != null && "PENDING".equals(r.getStayRequestStatus())) {
            flash.addFlashAttribute("error", "A stay change is unavailable for this reservation."); return "redirect:/customer/reservations";
        }
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Manila");
        LocalDate today = LocalDate.now(zone);
        if ("EARLY_CHECKOUT".equals(type)) {
            java.time.LocalDateTime now = java.time.LocalDateTime.now(zone);
            if (r.getCheckIn() == null || now.isBefore(r.getCheckIn().atTime(r.getCheckInTime() == null ? java.time.LocalTime.of(14, 0) : r.getCheckInTime()))) {
                flash.addFlashAttribute("error", "Early checkout can only be requested after check-in."); return "redirect:/customer/reservations";
            }
            r.setStayRequest(type, today);
        } else if ("EXTENSION".equals(type) && requestedDate != null && r.getCheckOut() != null && requestedDate.isAfter(r.getCheckOut())) {
            r.setStayRequest(type, requestedDate);
        } else {
            flash.addFlashAttribute("error", "Choose a valid extension date after your current checkout."); return "redirect:/customer/reservations";
        }
        reservations.save(r); flash.addFlashAttribute("success", "Your stay change request was sent to the manager.");
        return "redirect:/customer/reservations";
    }

    @PostMapping("/manager/reservations/{id}/stay-request")
    public String reviewStayChange(@PathVariable Long id, @RequestParam String decision, RedirectAttributes flash) {
        Reservation r = reservations.findById(id).orElse(null);
        if (r == null || r.getStayRequestType() == null || !"PENDING".equals(r.getStayRequestStatus())) {
            flash.addFlashAttribute("error", "Pending stay change request not found."); return "redirect:/manager/reservation-requests";
        }
        if ("REJECTED".equals(decision)) {
            r.setStayRequestStatus("REJECTED"); reservations.save(r); flash.addFlashAttribute("success", "Stay change request rejected.");
        } else if ("APPROVED".equals(decision) && "EARLY_CHECKOUT".equals(r.getStayRequestType())) {
            java.time.ZoneId zone = java.time.ZoneId.of("Asia/Manila");
            java.time.LocalDateTime now = java.time.LocalDateTime.now(zone).withNano(0);
            if (now.isBefore(r.getCheckIn().atTime(r.getCheckInTime() == null ? java.time.LocalTime.of(14, 0) : r.getCheckInTime()))) {
                flash.addFlashAttribute("error", "Cannot approve early checkout before the stay begins."); return "redirect:/manager/reservation-requests";
            }
            r.setCheckOut(now.toLocalDate()); r.setCheckOutTime(now.toLocalTime()); r.setCheckedOutAt(java.time.Instant.now()); r.setStatus("COMPLETED"); r.setStayRequestStatus("APPROVED"); reservations.save(r);
            flash.addFlashAttribute("success", "Early checkout completed. Final room charges are shown on the receipt.");
        } else if ("APPROVED".equals(decision) && "EXTENSION".equals(r.getStayRequestType())) {
            LocalDate newDate = r.getStayRequestDate();
            boolean conflict = reservations.findAll().stream().filter(other -> !other.getId().equals(r.getId()))
                    .filter(other -> other.getRoom() != null && r.getRoom() != null && other.getRoom().getId().equals(r.getRoom().getId()))
                    .filter(other -> java.util.List.of("PENDING", "PENCIL_PENDING_REVIEW", "APPROVED", "CANCELLATION_REQUESTED").contains(other.getStatus()))
                    .anyMatch(other -> other.getCheckIn() != null && other.getCheckOut() != null && other.getCheckIn().isBefore(newDate) && other.getCheckOut().isAfter(r.getCheckOut()));
            if (conflict) { flash.addFlashAttribute("error", "The requested extension overlaps another reservation."); return "redirect:/manager/reservation-requests"; }
            r.setCheckOut(newDate); r.setStayRequestStatus("APPROVED"); reservations.save(r);
            flash.addFlashAttribute("success", "Extension approved. The additional room charge is reflected in the updated receipt; payment remains due at reception.");
        } else { flash.addFlashAttribute("error", "Choose a valid decision."); return "redirect:/manager/reservation-requests"; }
        return "redirect:/manager/reservation-requests";
    }

    @GetMapping("/api/rooms/{roomId}/availability")
    @ResponseBody
    public java.util.List<java.util.Map<String, String>> roomAvailability(@PathVariable Long roomId) {
        return reservations.findAll().stream()
            .filter(r -> r.getRoom() != null && r.getRoom().getId() != null && r.getRoom().getId().equals(roomId))
            .filter(r -> r.getCheckIn() != null && r.getCheckOut() != null)
            .filter(r -> {
                java.time.ZoneId zone = java.time.ZoneId.of("Asia/Manila");
                java.time.LocalDateTime now = java.time.LocalDateTime.now(zone);
                java.time.LocalTime outTime = r.getCheckOutTime() == null ? java.time.LocalTime.NOON : r.getCheckOutTime();
                java.time.LocalDateTime end = r.getCheckOut().atTime(outTime);
                if (!end.isAfter(now)) return false;
                return java.util.List.of(
                        "PENDING",
                        "PENCIL_PENDING_REVIEW",
                        "APPROVED",
                        "CANCELLATION_REQUESTED"
                ).contains(r.getStatus());
            })
            .map(r -> {
                java.time.LocalTime inTime = r.getCheckInTime() == null
                        ? java.time.LocalTime.of(14, 0) : r.getCheckInTime();
                java.time.LocalTime outTime = r.getCheckOutTime() == null
                        ? java.time.LocalTime.NOON : r.getCheckOutTime();
                return java.util.Map.of(
                    "checkIn", r.getCheckIn().toString(),
                    "checkOut", r.getCheckOut().toString(),
                    "checkInTime", inTime.toString(),
                    "checkOutTime", outTime.toString(),
                    "status", r.getStatus()
                );
            })
            .toList();
    }

    @GetMapping("/manager") public String manager(Model model) {
        ensureCustomerCodes();
        model.addAttribute("rooms", rooms.findAll());
        model.addAttribute("reservations", reservations.findAllByOrderByCreatedAtDesc());
        java.time.ZoneId hotelZone = java.time.ZoneId.of("Asia/Manila");
        java.time.LocalDateTime now = java.time.LocalDateTime.now(hotelZone);
        java.util.List<Reservation> occupancyReservations = reservations.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> "APPROVED".equals(r.getStatus()))
                .filter(r -> r.getCheckIn() != null && r.getCheckOut() != null)
                .filter(r -> {
                    java.time.LocalTime inTime = r.getCheckInTime() == null ? java.time.LocalTime.of(14, 0) : r.getCheckInTime();
                    java.time.LocalTime outTime = r.getCheckOutTime() == null ? java.time.LocalTime.NOON : r.getCheckOutTime();
                    java.time.LocalDateTime start = r.getCheckIn().atTime(inTime);
                    java.time.LocalDateTime end = r.getCheckOut().atTime(outTime);
                    return !now.isBefore(start) && now.isBefore(end);
                })
                .toList();
        model.addAttribute("occupancyReservations", occupancyReservations);
        model.addAttribute("reports", reports.findAllByOrderByCreatedAtDesc().stream().filter(GuestReport::isVisible).toList());
        return "manager";
    }

    @GetMapping("/manager/revenue")
    public String revenue(@RequestParam(defaultValue = "DAILY") String period, @RequestParam(required = false) LocalDate date, Model model) {
        RevenueReportService.Period selected;
        try { selected = RevenueReportService.Period.valueOf(period.toUpperCase(java.util.Locale.ROOT)); }
        catch (RuntimeException ex) { selected = RevenueReportService.Period.DAILY; }
        LocalDate reference = date == null ? LocalDate.now(java.time.ZoneId.of("Asia/Manila")) : date;
        RevenueReportService.Report report = revenueReportService.report(selected, reference);
        java.math.BigDecimal max = report.buckets().stream().map(RevenueReportService.Bucket::amount).max(java.math.BigDecimal::compareTo).orElse(java.math.BigDecimal.ZERO);
        java.util.Map<String,Integer> barWidths = new java.util.LinkedHashMap<>();
        for (RevenueReportService.Bucket bucket : report.buckets()) barWidths.put(bucket.label(), max.signum() == 0 ? 0 : bucket.amount().multiply(java.math.BigDecimal.valueOf(100)).divide(max, 0, java.math.RoundingMode.HALF_UP).intValue());
        model.addAttribute("report", report); model.addAttribute("period", selected.name()); model.addAttribute("referenceDate", reference); model.addAttribute("barWidths", barWidths);
        return "revenue-report";
    }

    @GetMapping("/manager/reservation-requests") public String managerReservationRequests(Model model) {
        ensureCustomerCodes();
        model.addAttribute("reservations", reservations.findAllByOrderByCreatedAtDesc());
        return "reservation-requests";
    }

    /**
     * Displays Pencil Bookings that are waiting for the Online Manager.
     * The review deadline is calculated from the successful down payment.
     */
    @GetMapping("/manager/pencil-bookings")
    public String managerPencilBookings(Model model) {
        java.util.List<Reservation> pencilBookings =
                reservations.findAllByOrderByCreatedAtDesc()
                        .stream()
                        .filter(r -> "PENCIL".equals(r.getBookingType()))
                        .filter(r -> "PENCIL_PENDING_REVIEW".equals(r.getStatus()))
                        .toList();

        java.util.Map<Long, Payment> downPayments = new java.util.HashMap<>();
        java.util.Map<Long, java.time.LocalDateTime> reviewDeadlines =
                new java.util.HashMap<>();
        java.util.Map<Long, Boolean> expiredReviews = new java.util.HashMap<>();

        java.time.Instant now = java.time.Instant.now();
        java.time.ZoneId hotelZone = java.time.ZoneId.of("Asia/Manila");

        for (Reservation reservation : pencilBookings) {
            payments.findByReservationIdAndPaymentTypeAndStatus(
                    reservation.getId(),
                    "PENCIL_DOWN_PAYMENT",
                    "PAID")
                .ifPresent(payment -> {
                    downPayments.put(reservation.getId(), payment);

                    if (payment.getPaidAt() != null) {
                        java.time.Instant deadline =
                                payment.getPaidAt().plus(java.time.Duration.ofHours(72));

                        reviewDeadlines.put(
                                reservation.getId(),
                                java.time.LocalDateTime.ofInstant(deadline, hotelZone));

                        expiredReviews.put(
                                reservation.getId(),
                                !now.isBefore(deadline));
                    }
                });
        }

        model.addAttribute("pencilBookings", pencilBookings);
        model.addAttribute("pencilDownPayments", downPayments);
        model.addAttribute("pencilReviewDeadlines", reviewDeadlines);
        model.addAttribute("pencilReviewExpired", expiredReviews);

        return "pencil-bookings";
    }

    /**
     * Sends an approval or rejection decision to the dedicated Pencil
     * Booking service, which enforces the 72-hour review deadline.
     */
    @PostMapping("/manager/pencil-bookings/{id}/review")
    public String reviewPencilBooking(
            @PathVariable Long id,
            @RequestParam String decision,
            RedirectAttributes flash) {

        try {
            Reservation reviewed =
                    pencilBookingService.reviewBooking(id, decision);

            flash.addFlashAttribute(
                    "success",
                    "Pencil Booking " + reviewed.getId() + " was "
                            + reviewed.getStatus().toLowerCase(java.util.Locale.ROOT) + ".");

        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute(
                    "error",
                    ex.getMessage() == null
                            ? "Could not process this Pencil Booking decision."
                            : ex.getMessage());
        }

        return "redirect:/manager/pencil-bookings";
    }

    @GetMapping("/manager/history")
    public String managerHistory(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "all") String period,
            @RequestParam(required = false) LocalDate date,
            Model model) {
        ensureCustomerCodes();
        java.time.ZoneId hotelZone = java.time.ZoneId.of("Asia/Manila");
        LocalDate referenceDate = date == null ? LocalDate.now(hotelZone) : date;
        LocalDate rangeStart = null;
        LocalDate rangeEnd = null;
        switch (period == null ? "all" : period.toLowerCase(java.util.Locale.ROOT)) {
            case "day" -> { rangeStart = referenceDate; rangeEnd = referenceDate; }
            case "week" -> {
                rangeStart = referenceDate.minusDays(referenceDate.getDayOfWeek().getValue() - 1L);
                rangeEnd = rangeStart.plusDays(6);
            }
            case "month" -> {
                rangeStart = referenceDate.withDayOfMonth(1);
                rangeEnd = referenceDate.withDayOfMonth(referenceDate.lengthOfMonth());
            }
            case "year" -> {
                rangeStart = referenceDate.withDayOfYear(1);
                rangeEnd = referenceDate.withDayOfYear(referenceDate.lengthOfYear());
            }
            default -> period = "all";
        }
        final LocalDate from = rangeStart;
        final LocalDate to = rangeEnd;
        final String term = q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT);
        java.util.List<Reservation> history = reservations.findAllByOrderByCreatedAtDesc().stream()
                .filter(this::isProcessedHistory)
                .filter(r -> from == null || (r.getCheckIn() != null &&
                        !r.getCheckIn().isBefore(from) && !r.getCheckIn().isAfter(to)))
                // Search registered customers and walk-in guests without assuming
                // that every reservation has an AppUser customer.
                .filter(r -> term.isBlank() || matchesHistorySearch(r, term))
                .toList();
        model.addAttribute("reservations", history);
        model.addAttribute("searchQuery", q == null ? "" : q);
        model.addAttribute("period", period);
        model.addAttribute("referenceDate", referenceDate);
        return "reservation-history";
    }

    @GetMapping("/manager/room-management") public String roomManagement(Model model) {
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Manila");
        java.time.LocalDateTime now = java.time.LocalDateTime.now(zone);
        java.util.Map<Long, String> roomReservationWarnings = new java.util.HashMap<>();
        for (Room room : rooms.findAll()) {
            java.util.List<Reservation> activeBookings = reservations.findAllByOrderByCreatedAtDesc().stream()
                    .filter(r -> r.getRoom() != null && room.getId().equals(r.getRoom().getId()))
                    .filter(r -> r.getCheckIn() != null && r.getCheckOut() != null)
                    .filter(r -> !java.util.List.of("REJECTED", "CANCELLED", "COMPLETED", "VOID").contains(r.getStatus()))
                    .filter(r -> {
                        java.time.LocalTime outTime = r.getCheckOutTime() == null ? java.time.LocalTime.NOON : r.getCheckOutTime();
                        return r.getCheckOut().atTime(outTime).isAfter(now);
                    })
                    .toList();
            String details = activeBookings.stream().limit(3)
                    .map(r -> r.getStatus() + " · " + r.getCheckIn() + " " +
                            (r.getCheckInTime() == null ? "14:00" : r.getCheckInTime()) + " to " +
                            r.getCheckOut() + " " +
                            (r.getCheckOutTime() == null ? "12:00" : r.getCheckOutTime()))
                    .collect(java.util.stream.Collectors.joining("; "));
            String warning = activeBookings.isEmpty()
                    ? "Deactivate this room? It will be hidden from new bookings, but its reservation history will be kept."
                    : "This room has " + activeBookings.size() + " pending or current/upcoming reservation(s): " +
                      details + (activeBookings.size() > 3 ? "; and " + (activeBookings.size() - 3) + " more" : "") +
                      ". Deactivating it will not cancel those bookings. Continue anyway?";
            roomReservationWarnings.put(room.getId(), warning);
        }
        model.addAttribute("rooms", rooms.findAll());
        model.addAttribute("roomReservationWarnings", roomReservationWarnings);
        return "room-management";
    }

    @GetMapping("/manager/rooms/new")
    public String newRoom(Model model) { model.addAttribute("room", new Room("", "Standard", java.math.BigDecimal.ZERO, 2)); return "room-form"; }

    @GetMapping("/manager/rooms/{id}/edit")
    public String editRoom(@PathVariable Long id, Model model, RedirectAttributes flash) {
        Room room = rooms.findById(id).orElse(null);
        if (room == null) { flash.addFlashAttribute("error", "Room not found."); return "redirect:/manager/room-management"; }
        model.addAttribute("room", room); return "room-form";
    }

    @PostMapping("/manager/rooms")
    public String createRoom(@RequestParam String roomNumber, @RequestParam String type, @RequestParam java.math.BigDecimal nightlyRate,
            @RequestParam int capacity, @RequestParam(defaultValue = "") String description,
            @RequestParam(name = "photos", required = false) java.util.List<MultipartFile> photos, RedirectAttributes flash) {
        String cleanNumber = roomNumber == null ? "" : roomNumber.trim();
        String cleanType = type == null ? "" : type.trim();
        if (cleanNumber.isBlank() || cleanNumber.length() > 30 || cleanType.isBlank() || cleanType.length() > 60 || nightlyRate.signum() < 0 || capacity < 1 || capacity > 10 || description.length() > 2000 || rooms.existsByRoomNumber(cleanNumber)) {
            flash.addFlashAttribute("error", "Enter a unique room number, room type, non-negative rate, capacity from 1–10, and description up to 2,000 characters.");
            return "redirect:/manager/rooms/new";
        }
        Room room = new Room(cleanNumber, cleanType, nightlyRate, capacity); room.setDescription(description.trim());
        try { room.setPhotos(roomPhotoService.storeFive(photos)); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); return "redirect:/manager/rooms/new"; }
        rooms.save(room);
        flash.addFlashAttribute("success", "Room added."); return "redirect:/manager/room-management";
    }

    @PostMapping("/manager/rooms/{id}")
    public String updateRoom(@PathVariable Long id, @RequestParam String roomNumber, @RequestParam String type, @RequestParam java.math.BigDecimal nightlyRate,
            @RequestParam int capacity, @RequestParam(defaultValue = "") String description,
            @RequestParam(name = "photos", required = false) java.util.List<MultipartFile> photos, RedirectAttributes flash) {
        Room room = rooms.findById(id).orElse(null);
        if (room == null) { flash.addFlashAttribute("error", "Room not found."); return "redirect:/manager/room-management"; }
        boolean duplicate = rooms.findAll().stream().anyMatch(other -> !other.getId().equals(id) && other.getRoomNumber().equalsIgnoreCase(roomNumber.trim()));
        if (roomNumber.isBlank() || roomNumber.length() > 30 || type.isBlank() || type.length() > 60 || nightlyRate.signum() < 0 || capacity < 1 || capacity > 10 || description.length() > 2000 || duplicate) {
            flash.addFlashAttribute("error", "Enter a unique room number, room type, non-negative rate, capacity from 1–10, and description up to 2,000 characters.");
            return "redirect:/manager/rooms/" + id + "/edit";
        }
        if (photos != null && photos.stream().anyMatch(file -> !file.isEmpty())) {
            try { room.setPhotos(roomPhotoService.storeFive(photos)); }
            catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); return "redirect:/manager/rooms/" + id + "/edit"; }
        }
        room.setRoomNumber(roomNumber.trim()); room.setType(type.trim()); room.setNightlyRate(nightlyRate); room.setCapacity(capacity); room.setDescription(description.trim()); rooms.save(room);
        flash.addFlashAttribute("success", "Room updated. Existing reservation rates remain unchanged."); return "redirect:/manager/room-management";
    }

    @GetMapping("/room-photos/{name}")
    @ResponseBody
    public org.springframework.http.ResponseEntity<byte[]> roomPhoto(@PathVariable String name) throws java.io.IOException {
        byte[] image = roomPhotoService.load(name);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(roomPhotoService.contentType(name)))
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "public, max-age=86400").body(image);
    }

    @PostMapping("/customer/reports") public String submitReport(Authentication auth,
            @RequestParam String category, @RequestParam String message, RedirectAttributes flash) {
        String clean = message == null ? "" : message.trim();
        if (clean.isBlank() || clean.length() > 2000 || category == null ||
                !java.util.List.of("SERVICE", "ROOM", "BOOKING", "WEBSITE", "OTHER").contains(category)) {
            flash.addFlashAttribute("error", "Choose a report category and enter a message of up to 2,000 characters.");
            return "redirect:/customer/reservations#report";
        }
        AppUser customer = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        reports.save(new GuestReport(customer, category, clean));
        flash.addFlashAttribute("success", "Thank you. Your report was sent to the hotel manager.");
        return "redirect:/customer/reservations#report";
    }

    @GetMapping("/manager/reports") public String managerReports(Model model) {
        ensureCustomerCodes();
        // Deleted reports remain stored for history, but must not appear in the active manager report list.
        model.addAttribute("reports", reports.findAllByOrderByCreatedAtDesc().stream()
                .filter(GuestReport::isVisible)
                .toList());
        return "reports";
    }

    @PostMapping("/manager/reports/{id}/status") public String updateReportStatus(@PathVariable Long id,
            @RequestParam @Pattern(regexp="OPEN|RESOLVED") String status, RedirectAttributes flash) {
        GuestReport report = reports.findById(id).orElse(null);
        if (report == null || !report.isVisible()) {
            flash.addFlashAttribute("error", "Report not found.");
        } else {
            report.setStatus(status);
            reports.save(report);
            flash.addFlashAttribute("success", "Report status updated.");
        }
        return "redirect:/manager/reports";
    }

    @PostMapping("/manager/reports/{id}/reply")
    public String managerReplyToReport(@PathVariable Long id, @RequestParam String message, RedirectAttributes flash) {
        GuestReport report = reports.findById(id).orElse(null);
        String clean = message == null ? "" : message.trim();
        if (report == null || !report.isVisible()) {
            flash.addFlashAttribute("error", "Report not found.");
        } else if (clean.isBlank() || clean.length() > 2000) {
            flash.addFlashAttribute("error", "Enter a reply of up to 2,000 characters.");
        } else {
            report.appendConversationMessage("MANAGER", clean);
            reports.save(report);
            flash.addFlashAttribute("success", "Reply sent to the guest.");
        }
        return "redirect:/manager/reports";
    }

    @PostMapping("/customer/reports/{id}/reply")
    public String customerReplyToReport(@PathVariable Long id, Authentication auth,
            @RequestParam String message, RedirectAttributes flash) {
        GuestReport report = reports.findById(id).orElse(null);
        String clean = message == null ? "" : message.trim();
        if (report == null || !report.isVisible() || report.getCustomer() == null ||
                !report.getCustomer().getEmail().equalsIgnoreCase(auth.getName())) {
            flash.addFlashAttribute("error", "Report not found.");
        } else if (clean.isBlank() || clean.length() > 2000) {
            flash.addFlashAttribute("error", "Enter a reply of up to 2,000 characters.");
        } else {
            report.appendConversationMessage("GUEST", clean);
            reports.save(report);
            flash.addFlashAttribute("success", "Message sent to the manager.");
        }
        return "redirect:/customer/reservations#report";
    }

    @PostMapping("/manager/reports/{id}/delete")
    public String deleteGuestReport(@PathVariable Long id, RedirectAttributes flash) {
        GuestReport report = reports.findById(id).orElse(null);
        if (report == null) {
            flash.addFlashAttribute("error", "Report not found.");
        } else {
            report.setVisible(false);
            reports.save(report);
            flash.addFlashAttribute("success", "Guest report removed from the manager report list.");
        }
        return "redirect:/manager/reports";
    }

    @PostMapping("/manager/rooms/{id}/deactivate") public String deactivateRoom(@PathVariable Long id, RedirectAttributes flash) {
        Room room = rooms.findById(id).orElse(null);
        if (room == null) {
            flash.addFlashAttribute("error", "Room not found.");
        } else if (!room.isActive()) {
            flash.addFlashAttribute("error", "This room is already inactive.");
        } else {
            room.setActive(false);
            rooms.save(room);
            flash.addFlashAttribute("success", "Room " + room.getRoomNumber() + " was deactivated and hidden from new bookings.");
        }
        return "redirect:/manager";
    }

    @PostMapping("/manager/rooms/{id}/reactivate") public String reactivateRoom(@PathVariable Long id, RedirectAttributes flash) {
        Room room = rooms.findById(id).orElse(null);
        if (room == null) {
            flash.addFlashAttribute("error", "Room not found.");
        } else if (room.isActive()) {
            flash.addFlashAttribute("error", "This room is already active.");
        } else {
            room.setActive(true);
            rooms.save(room);
            flash.addFlashAttribute("success", "Room " + room.getRoomNumber() + " was reactivated.");
        }
        return "redirect:/manager";
    }

    @PostMapping("/manager/reservations/{id}/status") public String updateStatus(@PathVariable Long id,
            @RequestParam @Pattern(regexp="APPROVED|REJECTED|CANCELLED") String status, RedirectAttributes flash) {
        try {
            reservationService.updateStatus(id, status);
            flash.addFlashAttribute("success", "Reservation status updated.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager";
    }


    /**
     * Checks whether a reservation matches the manager's history search.
     *
     * Registered online guests are searched through AppUser.
     * Walk-in guests are searched through WalkInGuest.
     * Shared reservation and room information is searchable for both.
     */
    private boolean matchesHistorySearch(Reservation r, String term) {
        boolean matchesRegisteredCustomer = r.getCustomer() != null
                && (
                    r.getCustomer().getFullName() != null
                        && r.getCustomer().getFullName().toLowerCase(java.util.Locale.ROOT).contains(term)
                    || r.getCustomer().getEmail() != null
                        && r.getCustomer().getEmail().toLowerCase(java.util.Locale.ROOT).contains(term)
                    || r.getCustomer().getCustomerCode() != null
                        && r.getCustomer().getCustomerCode().toLowerCase(java.util.Locale.ROOT).contains(term)
                );

        boolean matchesWalkInGuest = r.getWalkInGuest() != null
                && (
                    r.getWalkInGuest().getFullName() != null
                        && r.getWalkInGuest().getFullName().toLowerCase(java.util.Locale.ROOT).contains(term)
                    || r.getWalkInGuest().getEmail() != null
                        && r.getWalkInGuest().getEmail().toLowerCase(java.util.Locale.ROOT).contains(term)
                    || r.getWalkInGuest().getPhone() != null
                        && r.getWalkInGuest().getPhone().toLowerCase(java.util.Locale.ROOT).contains(term)
                );

        return String.valueOf(r.getId()).contains(term)
                || matchesRegisteredCustomer
                || matchesWalkInGuest
                || (r.getRoom().getType() != null
                    && r.getRoom().getType().toLowerCase(java.util.Locale.ROOT).contains(term))
                || (r.getRoom().getRoomNumber() != null
                    && r.getRoom().getRoomNumber().toLowerCase(java.util.Locale.ROOT).contains(term))
                || (r.getStatus() != null
                    && r.getStatus().toLowerCase(java.util.Locale.ROOT).contains(term))
                || (r.getBookingType() != null
                    && r.getBookingType().toLowerCase(java.util.Locale.ROOT).contains(term));
    }

    /**
     * Only completed or otherwise processed reservations belong in history.
     * A Pencil Booking awaiting its Online Manager decision is still pending
     * and must not be hidden or deleted as a processed history record.
     */
    private boolean isProcessedHistory(Reservation r) {
        return r != null && r.isHistoryVisible()
                && !java.util.List.of(
                    "PENDING",
                    "PENCIL_PENDING_REVIEW",
                    "CANCELLATION_REQUESTED"
                ).contains(r.getStatus());
    }

    /** Soft-delete one processed history record without deleting its reservation. */
    @PostMapping("/manager/history/{id}/delete")
    public String deleteHistoryRecord(@PathVariable Long id, RedirectAttributes flash) {
        Reservation r = reservations.findById(id).orElse(null);
        if (r == null || !isProcessedHistory(r)) {
            flash.addFlashAttribute("error", "Processed history record not found.");
        } else {
            r.setHistoryVisible(false);
            reservations.save(r);
            flash.addFlashAttribute("success", "History record deleted.");
        }
        return "redirect:/manager/history";
    }

    /** Soft-delete all currently visible processed history records. */
    @PostMapping("/manager/history/clear")
    public String clearHistory(RedirectAttributes flash) {
        java.util.List<Reservation> processed = reservations.findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(this::isProcessedHistory)
                .toList();
        if (processed.isEmpty()) {
            flash.addFlashAttribute("error", "There are no processed history records to delete.");
        } else {
            processed.forEach(r -> r.setHistoryVisible(false));
            reservations.saveAll(processed);
            flash.addFlashAttribute("success", "Processed reservation history cleared.");
        }
        return "redirect:/manager/history";
    }

    private boolean hasValidCustomerCode(AppUser customer) {
        return customer != null && customer.getCustomerCode() != null &&
                customer.getCustomerCode().matches("mtchlvr-\\d{4}-5201314");
    }

    /** Assign a readable, unique customer code to existing customer accounts too. */
    private void ensureCustomerCodes() {
        for (AppUser customer : users.findAll()) {
            if ("CUSTOMER".equalsIgnoreCase(customer.getRole()) && !hasValidCustomerCode(customer)) {
                customer.setCustomerCode(generateCustomerCode());
                users.save(customer);
            }
        }
    }

    private String generateCustomerCode() {
        String code;
        do {
            int digits = java.util.concurrent.ThreadLocalRandom.current().nextInt(10_000);
            code = String.format(java.util.Locale.ROOT, "mtchlvr-%04d-5201314", digits);
        } while (users.existsByCustomerCode(code));
        return code;
    }

}
