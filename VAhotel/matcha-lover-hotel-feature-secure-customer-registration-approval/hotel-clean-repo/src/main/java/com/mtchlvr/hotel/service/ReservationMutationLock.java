package com.mtchlvr.hotel.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Serializes reservation availability checks and state changes in this app instance. */
@Component
public class ReservationMutationLock {
    private final ReentrantLock lock = new ReentrantLock(true);

    public <T> T withLock(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
