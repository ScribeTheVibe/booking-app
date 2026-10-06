package com.bookingapp.bookingapp.scheduler;

import com.bookingapp.bookingapp.entity.Booking;
import com.bookingapp.bookingapp.entity.BookingStatus;
import com.bookingapp.bookingapp.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BookingCompletionScheduler {

    private final BookingRepository bookingRepository;

    @Scheduled(cron = "0 0 0,12 * * *")
    @Transactional
    public void completeExpiredBookings() {
        List<Booking> expired = bookingRepository
                .findByStatusAndEndTimeBefore(BookingStatus.CONFIRMED, LocalDateTime.now());

        for (Booking booking : expired) {
            booking.setStatus(BookingStatus.COMPLETED);
        }
    }
}