package com.mtchlvr.hotel.web;

import com.mtchlvr.hotel.model.Reservation;
import com.mtchlvr.hotel.repo.ReservationRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ReceiptController {
    private final ReservationRepository reservations;
    public ReceiptController(ReservationRepository reservations) { this.reservations = reservations; }

    @GetMapping("/customer/reservations/{id}/receipt")
    public String customerReceipt(@PathVariable Long id, Authentication auth, Model model) {
        Reservation r = reservations.findById(id).orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (r.getCustomer() == null || !r.getCustomer().getEmail().equalsIgnoreCase(auth.getName())) throw new org.springframework.security.access.AccessDeniedException("Not your reservation.");
        model.addAttribute("reservation", r); model.addAttribute("finalReceipt", "COMPLETED".equals(r.getStatus()));
        return "reservation-receipt";
    }

    @GetMapping("/manager/reservations/{id}/receipt")
    public String managerReceipt(@PathVariable Long id, Model model) {
        Reservation r = reservations.findById(id).orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        model.addAttribute("reservation", r); model.addAttribute("finalReceipt", "COMPLETED".equals(r.getStatus()));
        return "reservation-receipt";
    }
}
