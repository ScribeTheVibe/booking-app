package com.bookingapp.bookingapp.service;

import com.bookingapp.bookingapp.dto.BookingItemRequest;
import com.bookingapp.bookingapp.entity.*;
import com.bookingapp.bookingapp.exception.InsufficientAvailabilityException;
import com.bookingapp.bookingapp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:booking_service_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
@Import(BookingServiceIntegrationTest.TimeConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookingServiceIntegrationTest {

    @TestConfiguration
    static class TimeConfig {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-10-20T06:00:00Z"), ResortTime.ZONE);
        }
    }

    @Autowired private BookingService service;
    @Autowired private UserRepository users;
    @Autowired private BookableResourceRepository resources;
    @Autowired private BookingRepository bookings;
    @Autowired private BookingItemRepository items;
    @Autowired private SettingRepository settings;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    private final LocalDate friday = LocalDate.of(2026, 10, 23);
    private Long userId;
    private Long ticketId;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString();
        userId = users.save(new User("Test", "User", "hash", unique + "@example.com", unique.substring(0, 24))).getId();
        ticketId = resources.save(new BookableResource("Pass", "Ski pass", ResourceType.TICKET,
                new BigDecimal("25.00"), 1)).getId();
        settings.save(new Setting(Setting.RESCHEDULE_DEADLINE_HOURS, "24"));
    }

    @Test
    void fullDayUsesBothHalvesAndFailedMovePreservesPersistedBooking() {
        Booking original = create(friday, Session.FULL_DAY);
        create(friday.plusDays(1), Session.EVENING);
        assertThrows(InsufficientAvailabilityException.class,
                () -> create(friday, Session.MORNING));
        assertThrows(InsufficientAvailabilityException.class,
                () -> create(friday, Session.EVENING));
        assertThrows(InsufficientAvailabilityException.class,
                () -> service.rescheduleMyBooking(original.getId(), userId, friday.plusDays(1)));
        Booking unchanged = service.getMyBooking(original.getId(), userId);
        assertEquals(friday, unchanged.getDate());
        assertEquals(BookingStatus.PENDING, unchanged.getStatus());
        assertEquals(new BigDecimal("25.00"), unchanged.totalPrice());
    }

    @Test
    void confirmedMoveReleasesOldSlotAndKeepsPriceSnapshot() {
        Booking original = create(friday, Session.MORNING);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            bookings.findById(original.getId()).orElseThrow().confirm(clock);
            resources.findById(ticketId).orElseThrow().changeUnitPrice(new BigDecimal("99.00"));
        });
        service.rescheduleMyBooking(original.getId(), userId, friday.plusDays(1));
        Booking moved = service.getMyBooking(original.getId(), userId);
        assertEquals(friday.plusDays(1), moved.getDate());
        assertEquals(BookingStatus.CONFIRMED, moved.getStatus());
        assertEquals(new BigDecimal("25.00"), moved.totalPrice());
        assertEquals(new BigDecimal("99.00"), create(friday, Session.MORNING).totalPrice());
        assertThrows(InsufficientAvailabilityException.class,
                () -> create(friday.plusDays(1), Session.MORNING));
    }

    @Test
    void rejectionFreesCapacityInBothUsageQueries() {
        Booking original = create(friday, Session.FULL_DAY);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                bookings.findById(original.getId()).orElseThrow().reject(clock));
        assertTrue(items.findUsage(List.of(ticketId), friday, friday).isEmpty());
        assertTrue(items.findUsageExcludingBooking(List.of(ticketId), friday, friday, -1L).isEmpty());
        assertEquals(BookingStatus.PENDING, create(friday, Session.FULL_DAY).getStatus());
    }

    @Test
    void concurrentCreatesCannotOverbookLastUnit() throws Exception {
        assertOneSuccess(() -> create(friday, Session.MORNING), () -> create(friday, Session.MORNING));
        assertEquals(1L, items.findUsage(List.of(ticketId), friday, friday).getFirst().quantity());
    }

    @Test
    void createAndRescheduleCompeteForSameLastUnit() throws Exception {
        Booking original = create(friday, Session.MORNING);
        LocalDate target = friday.plusDays(1);
        assertOneSuccess(() -> create(target, Session.MORNING),
                () -> service.rescheduleMyBooking(original.getId(), userId, target));
        assertEquals(1L, items.findUsage(List.of(ticketId), target, target).getFirst().quantity());
        Booking after = service.getMyBooking(original.getId(), userId);
        assertTrue(after.getDate().equals(friday) || after.getDate().equals(target));
        assertEquals(new BigDecimal("25.00"), after.totalPrice());
    }

    private Booking create(LocalDate date, Session session) {
        return service.createBooking(userId, date, session, 1, List.of(new BookingItemRequest(ticketId, 1)));
    }

    private void assertOneSuccess(Callable<Booking> first, Callable<Booking> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(() -> attempt(first, ready, start));
            var secondResult = executor.submit(() -> attempt(second, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, firstResult.get(10, TimeUnit.SECONDS) + secondResult.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
        }
    }

    private int attempt(Callable<Booking> operation, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test did not start");
        try {
            operation.call();
            return 1;
        } catch (InsufficientAvailabilityException expected) {
            return 0;
        }
    }
}