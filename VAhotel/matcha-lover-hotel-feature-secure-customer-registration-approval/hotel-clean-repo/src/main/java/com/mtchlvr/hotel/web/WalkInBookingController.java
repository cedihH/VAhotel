package com.mtchlvr.hotel.web;

import com.mtchlvr.hotel.model.Payment;
import com.mtchlvr.hotel.model.Reservation;
import com.mtchlvr.hotel.model.Room;
import com.mtchlvr.hotel.model.WalkInGuest;
import com.mtchlvr.hotel.repo.PaymentRepository;
import com.mtchlvr.hotel.repo.ReservationRepository;
import com.mtchlvr.hotel.repo.RoomRepository;
import com.mtchlvr.hotel.repo.WalkInGuestRepository;
import com.mtchlvr.hotel.service.WalkInBookingService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Web endpoints for On-Site Manager walk-in reservations.
 * Walk-in guests are recorded separately and never need AppUser accounts.
 */
@Controller
@Validated
public class WalkInBookingController {
    private final WalkInBookingService walkInBookingService;
    private final RoomRepository rooms;
    private final ReservationRepository reservations;
    private final PaymentRepository payments;
    private final WalkInGuestRepository walkInGuests;

    public WalkInBookingController(
            WalkInBookingService walkInBookingService,
            RoomRepository rooms,
            ReservationRepository reservations,
            PaymentRepository payments,
            WalkInGuestRepository walkInGuests) {
        this.walkInBookingService = walkInBookingService;
        this.rooms = rooms;
        this.reservations = reservations;
        this.payments = payments;
        this.walkInGuests = walkInGuests;
    }

    @GetMapping("/manager/walk-ins/new")
    public String newWalkInBooking(Model model) {
        model.addAttribute("rooms",
                rooms.findByActiveTrueAndAvailableTrueOrderByTypeAscRoomNumberAsc());
        model.addAttribute("form", new WalkInForm());
        return "walk-in-booking";
    }

    @GetMapping("/manager/walk-ins/guests")
    public String walkInGuests(@RequestParam(defaultValue = "") String q, Model model) {
        String term = q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT);
        List<WalkInGuest> guests = walkInGuests.findAll().stream()
                .filter(g -> term.isBlank() || contains(g.getFullName(), term) || contains(g.getEmail(), term) || contains(g.getPhone(), term))
                .toList();
        java.util.Map<Long, List<Reservation>> reservationsByGuest = new java.util.HashMap<>();
        for (Reservation reservation : reservations.findAllByOrderByCreatedAtDesc()) {
            if (reservation.getWalkInGuest() != null && reservation.getWalkInGuest().getId() != null) {
                reservationsByGuest.computeIfAbsent(reservation.getWalkInGuest().getId(), ignored -> new java.util.ArrayList<>()).add(reservation);
            }
        }
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("guests", guests);
        model.addAttribute("reservationsByGuest", reservationsByGuest);
        return "walk-in-guests";
    }

    @PostMapping("/manager/walk-ins/guests/{id}")
    public String updateWalkInGuest(@PathVariable Long id, @RequestParam String fullName,
            @RequestParam(defaultValue = "") String email, @RequestParam(defaultValue = "") String phone,
            RedirectAttributes flash) {
        WalkInGuest guest = walkInGuests.findById(id).orElse(null);
        String name = fullName == null ? "" : fullName.trim();
        String cleanEmail = email == null ? "" : email.trim();
        String cleanPhone = phone == null ? "" : phone.trim();
        if (guest == null) flash.addFlashAttribute("error", "Walk-in guest not found.");
        else if (name.isBlank() || name.length() > 150 || cleanEmail.length() > 160 ||
                (!cleanEmail.isBlank() && !cleanEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) || cleanPhone.length() > 40) {
            flash.addFlashAttribute("error", "Enter a valid guest name, email, and phone number within the allowed lengths.");
        } else {
            guest.setFullName(name); guest.setEmail(cleanEmail.isBlank() ? null : cleanEmail); guest.setPhone(cleanPhone.isBlank() ? null : cleanPhone);
            walkInGuests.save(guest); flash.addFlashAttribute("success", "Walk-in guest details updated. Reservation history was kept.");
        }
        return "redirect:/manager/walk-ins/guests";
    }

    private boolean contains(String value, String term) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(term);
    }

    @PostMapping("/manager/walk-ins")
    public String createWalkInBooking(
            @RequestParam @NotBlank @Size(max = 150) String fullName,
            @RequestParam(required = false) @Email @Size(max = 160) String email,
            @RequestParam(required = false) @Size(max = 40) String phone,
            @RequestParam Long roomId,
            @RequestParam LocalDate checkIn,
            @RequestParam LocalDate checkOut,
            RedirectAttributes flash) {
        try {
            Reservation saved = walkInBookingService.createWalkInBooking(
                    fullName, email, phone, roomId,
                    checkIn, LocalTime.of(14, 0), checkOut, LocalTime.NOON);
            flash.addFlashAttribute("success",
                    "Walk-in reservation #" + saved.getId()
                            + " was recorded and fully paid.");
            return "redirect:/manager/walk-ins/" + saved.getId() + "/receipt";
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return "redirect:/manager/walk-ins/new";
        }
    }

    @GetMapping("/manager/walk-ins/{id}/receipt")
    public String walkInReceipt(@PathVariable Long id, Model model) {
        Reservation reservation = reservations.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Walk-in reservation not found."));
        if (!"WALK_IN".equals(reservation.getBookingType())
                || reservation.getWalkInGuest() == null
                || reservation.getCustomer() != null) {
            throw new IllegalArgumentException("This is not a valid walk-in reservation.");
        }
        List<Payment> paymentRecords =
                payments.findAllByReservationIdOrderByPaidAtAsc(id);
        model.addAttribute("reservation", reservation);
        model.addAttribute("payments", paymentRecords);
        model.addAttribute("fullPaymentRecorded", paymentRecords.stream().anyMatch(p ->
                "WALK_IN_FULL_PAYMENT".equals(p.getPaymentType())
                        && "PAID".equals(p.getStatus())
                        && p.getAmount() != null
                        && p.getAmount().compareTo(reservation.getTotalCost()) == 0));
        return "walk-in-receipt";
    }

    public static class WalkInForm {
        private String fullName;
        private String email;
        private String phone;
        private Long roomId;
        private LocalDate checkIn;
        private LocalTime checkInTime = LocalTime.of(14, 0);
        private LocalDate checkOut;
        private LocalTime checkOutTime = LocalTime.NOON;

        public String getFullName() { return fullName; }
        public void setFullName(String value) { fullName = value; }
        public String getEmail() { return email; }
        public void setEmail(String value) { email = value; }
        public String getPhone() { return phone; }
        public void setPhone(String value) { phone = value; }
        public Long getRoomId() { return roomId; }
        public void setRoomId(Long value) { roomId = value; }
        public LocalDate getCheckIn() { return checkIn; }
        public void setCheckIn(LocalDate value) { checkIn = value; }
        public LocalTime getCheckInTime() { return checkInTime; }
        public void setCheckInTime(LocalTime value) { checkInTime = value; }
        public LocalDate getCheckOut() { return checkOut; }
        public void setCheckOut(LocalDate value) { checkOut = value; }
        public LocalTime getCheckOutTime() { return checkOutTime; }
        public void setCheckOutTime(LocalTime value) { checkOutTime = value; }
    }
}
