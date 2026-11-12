package com.mtchlvr.hotel.service;

import com.mtchlvr.hotel.model.Payment;
import com.mtchlvr.hotel.model.Reservation;
import com.mtchlvr.hotel.repo.PaymentRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RevenueReportServiceTest {
    @Test void excludesSimulatedAndPendingPaymentsAndCountsCompletedLivePayments() {
        PaymentRepository repository = mock(PaymentRepository.class);
        Reservation complete = mock(Reservation.class); when(complete.getStatus()).thenReturn("COMPLETED");
        Reservation pending = mock(Reservation.class); when(pending.getStatus()).thenReturn("PENDING");
        Instant paid = LocalDate.of(2026, 11, 12).atTime(9, 0).atZone(ZoneId.of("Asia/Manila")).toInstant();
        when(repository.findAll()).thenReturn(List.of(
                new Payment(complete, new BigDecimal("1200"), "FULL", "CARD_GATEWAY", "PAID", "a", paid),
                new Payment(complete, new BigDecimal("800"), "FULL", "NEXUS_SIMULATION", "PAID", "b", paid),
                new Payment(pending, new BigDecimal("5000"), "FULL", "CARD_GATEWAY", "PAID", "c", paid)));
        RevenueReportService.Report report = new RevenueReportService(repository).report(RevenueReportService.Period.DAILY, LocalDate.of(2026, 11, 12));
        assertEquals(new BigDecimal("1200"), report.total());
        assertEquals(1, report.buckets().size());
    }
}
