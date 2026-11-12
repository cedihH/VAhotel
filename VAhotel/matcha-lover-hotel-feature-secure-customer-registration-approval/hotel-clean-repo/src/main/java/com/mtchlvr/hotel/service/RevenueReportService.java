package com.mtchlvr.hotel.service;

import com.mtchlvr.hotel.model.Payment;
import com.mtchlvr.hotel.repo.PaymentRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.WeekFields;
import java.util.*;

@Service
public class RevenueReportService {
    public enum Period { DAILY, WEEKLY, MONTHLY, ANNUAL }
    public record Bucket(String label, BigDecimal amount) {}
    public record Report(Period period, BigDecimal total, List<Bucket> buckets, boolean simulatedPaymentsExcluded) {}
    private final PaymentRepository payments;
    public RevenueReportService(PaymentRepository payments) { this.payments = payments; }

    public Report report(Period period, LocalDate referenceDate) {
        Objects.requireNonNull(period); Objects.requireNonNull(referenceDate);
        ZoneId zone = ZoneId.of("Asia/Manila");
        LocalDate from; LocalDate through;
        switch (period) {
            case DAILY -> { from = referenceDate; through = referenceDate; }
            case WEEKLY -> { from = referenceDate.with(DayOfWeek.MONDAY); through = from.plusDays(6); }
            case MONTHLY -> { from = referenceDate.withDayOfMonth(1); through = referenceDate.withDayOfMonth(referenceDate.lengthOfMonth()); }
            case ANNUAL -> { from = referenceDate.withDayOfYear(1); through = referenceDate.withDayOfYear(referenceDate.lengthOfYear()); }
            default -> throw new IllegalArgumentException("Unsupported reporting period.");
        }
        int count = switch (period) { case DAILY -> 1; case WEEKLY -> 7; case MONTHLY -> referenceDate.lengthOfMonth(); case ANNUAL -> 12; };
        Map<String, BigDecimal> sums = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            LocalDate date = period == Period.ANNUAL ? from.plusMonths(i) : from.plusDays(i);
            String key = period == Period.ANNUAL ? date.getMonth().toString() : date.toString();
            sums.put(key, BigDecimal.ZERO);
        }
        for (Payment p : payments.findAll()) {
            if (!"PAID".equals(p.getStatus()) || p.getPaidAt() == null || p.getReservation() == null ||
                    !"COMPLETED".equals(p.getReservation().getStatus()) ||
                    p.getPaymentMethod() == null || p.getPaymentMethod().toUpperCase(Locale.ROOT).contains("SIMULATION")) continue;
            LocalDate paidDate = p.getPaidAt().atZone(zone).toLocalDate();
            if (paidDate.isBefore(from) || paidDate.isAfter(through)) continue;
            String key = period == Period.ANNUAL ? paidDate.getMonth().toString() : paidDate.toString();
            sums.computeIfPresent(key, (k, old) -> old.add(p.getAmount()));
        }
        List<Bucket> buckets = sums.entrySet().stream().map(e -> new Bucket(e.getKey(), e.getValue())).toList();
        BigDecimal total = buckets.stream().map(Bucket::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Report(period, total, buckets, true);
    }
}
