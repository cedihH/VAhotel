package com.mtchlvr.hotel.web;

import com.mtchlvr.hotel.model.RefundRequest;
import com.mtchlvr.hotel.model.Reservation;
import com.mtchlvr.hotel.repo.RefundRequestRepository;
import com.mtchlvr.hotel.repo.ReservationRepository;
import com.mtchlvr.hotel.service.RefundService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RefundController {
    private final RefundService refundService;
    private final RefundRequestRepository refundRequests;
    private final ReservationRepository reservations;

    public RefundController(RefundService refundService,
                            RefundRequestRepository refundRequests,
                            ReservationRepository reservations) {
        this.refundService = refundService;
        this.refundRequests = refundRequests;
        this.reservations = reservations;
    }

    @GetMapping("/customer/reservations/{id}/refund")
    public String customerRefundPage(@PathVariable Long id,
                                     Authentication authentication,
                                     Model model) {
        Reservation reservation = reservations.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (reservation.getCustomer() == null
                || !reservation.getCustomer().getEmail().equalsIgnoreCase(authentication.getName())) {
            throw new IllegalArgumentException("Reservation not found.");
        }
        if (!"PENCIL".equals(reservation.getBookingType())
                || !"VOID".equals(reservation.getStatus())) {
            throw new IllegalArgumentException("This reservation is not eligible for a Pencil Booking refund.");
        }
        model.addAttribute("reservation", reservation);
        model.addAttribute("refundRequest",
                refundRequests.findByReservationId(id).orElse(null));
        return "customer-refund";
    }

    @PostMapping("/customer/reservations/{id}/refund")
    public String requestRefund(@PathVariable Long id,
                                Authentication authentication,
                                RedirectAttributes flash) {
        try {
            RefundRequest request = refundService.requestPencilRefund(id, authentication.getName());
            flash.addFlashAttribute("success",
                    "Refund request #" + request.getId() + " was submitted for manager review.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/customer/reservations/" + id + "/refund";
    }

    @GetMapping("/manager/refunds")
    public String managerRefunds(Model model) {
        model.addAttribute("refundRequests", refundRequests.findAllByOrderByCreatedAtDesc());
        return "refund-requests";
    }

    @PostMapping("/manager/refunds/{id}/review")
    public String reviewRefund(@PathVariable Long id,
                               @RequestParam String decision,
                               @RequestParam(required = false) String managerNote,
                               RedirectAttributes flash) {
        try {
            refundService.reviewRefundRequest(id, decision, managerNote);
            flash.addFlashAttribute("success", "Refund request decision saved.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager/refunds";
    }

    @PostMapping("/manager/refunds/{id}/process")
    public String processRefund(@PathVariable Long id, RedirectAttributes flash) {
        try {
            refundService.processApprovedRefund(id);
            flash.addFlashAttribute("success",
                    "Refund processed in the local Nexus simulation. Related payments are marked REFUNDED.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager/refunds";
    }
}
