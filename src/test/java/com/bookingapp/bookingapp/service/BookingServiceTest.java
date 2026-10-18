package com.bookingapp.bookingapp.service;

import com.bookingapp.bookingapp.dto.BookingItemRequest;
import com.bookingapp.bookingapp.dto.ResourceUsage;
import com.bookingapp.bookingapp.entity.*;
import com.bookingapp.bookingapp.exception.InsufficientAvailabilityException;
import com.bookingapp.bookingapp.exception.InvalidBookingStateException;
import com.bookingapp.bookingapp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingServiceTest {

    private final BookingRepository bookings = mock(BookingRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final BookableResourceRepository resources = mock(BookableResourceRepository.class);
    private final BookingItemRepository items = mock(BookingItemRepository.class);
    private final SettingRepository settings = mock(SettingRepository.class);
    private final Clock clock = mock(Clock.class);
    private final LocalDate friday = LocalDate.of(2026, 10, 23);
    private final User user = new User("Test", "User", "hash", "test@example.com", "+37060000000");
    private final BookableResource ticket = mock(BookableResource.class);
    private final BookableResource gear = mock(BookableResource.class);
    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookings, users, resources, items, settings, clock);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-20T06:00:00Z"));
        when(ticket.getId()).thenReturn(1L);
        when(ticket.getType()).thenReturn(ResourceType.TICKET);
        when(ticket.getUnitPrice()).thenReturn(new BigDecimal("25.00"));
        when(ticket.getTotalCapacity()).thenReturn(10);
        when(ticket.isActive()).thenReturn(true);
        when(gear.getId()).thenReturn(2L);
        when(gear.getType()).thenReturn(ResourceType.GEAR);
        when(gear.getUnitPrice()).thenReturn(new BigDecimal("15.00"));
        when(gear.getTotalCapacity()).thenReturn(10);
        when(gear.isActive()).thenReturn(true);
        when(resources.findAllById(any())).thenReturn(List.of(ticket, gear));
        when(resources.findAllByIdWithLock(any())).thenReturn(List.of(ticket, gear));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(bookings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(settings.findById(Setting.RESCHEDULE_DEADLINE_HOURS))
                .thenReturn(Optional.of(new Setting(Setting.RESCHEDULE_DEADLINE_HOURS, "24")));
    }

    @Test
    void createSnapshotsPricesAndMergesItemsWithIndependentGearQuantities() {
        Booking booking = service.createBooking(1L, friday, Session.FULL_DAY, 2,
                List.of(new BookingItemRequest(1L, 1), new BookingItemRequest(1L, 1),
                        new BookingItemRequest(2L, 5)));
        assertEquals(BookingStatus.PENDING, booking.getStatus());
        assertEquals(2, booking.getItems().size());
        assertEquals(new BigDecimal("125.00"), booking.totalPrice());
        when(ticket.getUnitPrice()).thenReturn(new BigDecimal("99.00"));
        assertEquals(new BigDecimal("125.00"), booking.totalPrice());
    }

    @Test
    void rejectsSessionThatStartsWhileWaitingForResourceLock() {
        when(resources.findAllByIdWithLock(any())).thenAnswer(invocation -> {
            when(clock.instant()).thenReturn(Session.MORNING.startAt(friday).toInstant());
            return List.of(ticket, gear);
        });
        assertThrows(IllegalArgumentException.class,
                () -> service.createBooking(1L, friday, Session.MORNING, 1, request(1)));
        verify(bookings, never()).save(any());
    }

    @Test
    void capacityAdditionMustNotOverflow() {
        when(ticket.getTotalCapacity()).thenReturn(Integer.MAX_VALUE);
        when(items.findUsage(any(), any(), any())).thenReturn(List.of(
                new ResourceUsage(1L, friday, Session.MORNING, BookingStatus.CONFIRMED,
                        (long) Integer.MAX_VALUE)));
        assertThrows(InsufficientAvailabilityException.class,
                () -> service.createBooking(1L, friday, Session.MORNING, 1, request(1)));
    }

    @Test
    void ticketTotalsMustNotOverflow() {
        BookableResource extraTicket = mock(BookableResource.class);
        when(extraTicket.getId()).thenReturn(3L);
        when(extraTicket.getType()).thenReturn(ResourceType.TICKET);
        when(extraTicket.getUnitPrice()).thenReturn(BigDecimal.ONE);
        when(extraTicket.getTotalCapacity()).thenReturn(10);
        when(extraTicket.isActive()).thenReturn(true);
        when(resources.findAllByIdWithLock(any())).thenReturn(List.of(ticket, gear, extraTicket));
        when(gear.getType()).thenReturn(ResourceType.TICKET);
        when(ticket.getTotalCapacity()).thenReturn(Integer.MAX_VALUE);
        when(gear.getTotalCapacity()).thenReturn(Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> service.createBooking(1L, friday,
                Session.MORNING, 1, List.of(new BookingItemRequest(1L, Integer.MAX_VALUE),
                        new BookingItemRequest(2L, Integer.MAX_VALUE), new BookingItemRequest(3L, 3))));
    }

    @Test
    void rejectsNullItemAndOverflowingDuplicateQuantities() {
        assertThrows(IllegalArgumentException.class, () -> service.createBooking(1L, friday,
                Session.MORNING, 1, Arrays.asList((BookingItemRequest) null)));
        assertThrows(IllegalArgumentException.class, () -> service.createBooking(1L, friday,
                Session.MORNING, 1, List.of(new BookingItemRequest(1L, Integer.MAX_VALUE),
                        new BookingItemRequest(1L, Integer.MAX_VALUE))));
    }

    @Test
    void fullDayNeedsBothHalvesButMorningDoesNotConsumeEvening() {
        when(items.findUsage(any(), any(), any())).thenReturn(List.of(
                new ResourceUsage(1L, friday, Session.MORNING, BookingStatus.PENDING, 10L)));
        var availability = service.findAvailability(1, request(1), friday, friday);
        assertEquals(3, availability.size());
        assertFalse(availability.get(0).available());
        assertTrue(availability.get(1).available());
        assertFalse(availability.get(2).available());
        assertTrue(availability.stream().allMatch(slot -> slot.totalPrice().equals(new BigDecimal("25.00"))));
    }

    @Test
    void pendingFullDayDropsAtStartAndReleasesEveningCapacity() {
        when(clock.instant()).thenReturn(Session.FULL_DAY.startAt(friday).toInstant());
        when(items.findUsage(any(), any(), any())).thenReturn(List.of(
                new ResourceUsage(1L, friday, Session.FULL_DAY, BookingStatus.PENDING, 10L)));
        var availability = service.findAvailability(1, request(1), friday, friday);
        assertEquals(1, availability.size());
        assertEquals(Session.EVENING, availability.getFirst().session());
        assertTrue(availability.getFirst().available());
    }

    @Test
    void confirmedFullDayStillHoldsEveningAfterMorningStarts() {
        when(clock.instant()).thenReturn(Session.FULL_DAY.startAt(friday).toInstant());
        when(items.findUsage(any(), any(), any())).thenReturn(List.of(
                new ResourceUsage(1L, friday, Session.FULL_DAY, BookingStatus.CONFIRMED, 10L)));
        assertFalse(service.findAvailability(1, request(1), friday, friday).getFirst().available());
    }

    @Test
    void failedRescheduleLeavesOriginalBookingUntouched() {
        Booking booking = ownedBooking(true);
        LocalDate target = friday.plusDays(1);
        when(items.findUsageExcludingBooking(any(), eq(target), eq(target), any()))
                .thenReturn(List.of(new ResourceUsage(1L, target, Session.FULL_DAY,
                        BookingStatus.CONFIRMED, 10L)));
        assertThrows(InsufficientAvailabilityException.class,
                () -> service.rescheduleMyBooking(7L, 1L, target));
        assertEquals(friday, booking.getDate());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals(new BigDecimal("25.00"), booking.totalPrice());
    }

    @Test
    void pendingAndConfirmedRescheduleWithoutRepricingOrChangingItems() {
        for (boolean confirmed : List.of(false, true)) {
            Booking booking = ownedBooking(confirmed);
            var originalItems = List.copyOf(booking.getItems());
            when(ticket.getUnitPrice()).thenReturn(new BigDecimal("99.00"));
            BigDecimal originalPrice = booking.totalPrice();
            service.rescheduleMyBooking(7L, 1L, friday.plusDays(1));
            assertEquals(friday.plusDays(1), booking.getDate());
            assertEquals(confirmed ? BookingStatus.CONFIRMED : BookingStatus.PENDING, booking.getStatus());
            assertEquals(Session.MORNING, booking.getSession());
            assertEquals(1, booking.getNumberOfPeople());
            assertEquals(originalItems, booking.getItems());
            assertEquals(originalPrice, booking.totalPrice());
        }
    }

    @Test
    void cutoffIsExclusiveAndRecheckedAfterWaitingForLock() {
        Booking booking = ownedBooking(false);
        Instant cutoff = booking.startsAt().toInstant().minusSeconds(24 * 3600);
        when(clock.instant()).thenReturn(cutoff.minusSeconds(1));
        when(resources.findAllByIdWithLock(any())).thenAnswer(invocation -> {
            when(clock.instant()).thenReturn(cutoff);
            return List.of(ticket, gear);
        });
        assertThrows(InvalidBookingStateException.class,
                () -> service.rescheduleMyBooking(7L, 1L, friday.plusDays(1)));
        assertEquals(friday, booking.getDate());
    }

    @Test
    void closedDatesStartedSessionsAndTicketMismatchAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createBooking(1L, friday.plusDays(3), Session.MORNING, 1, request(1)));
        assertThrows(IllegalArgumentException.class,
                () -> service.createBooking(1L, friday, Session.MORNING, 2, request(1)));
        when(clock.instant()).thenReturn(Session.MORNING.startAt(friday).toInstant());
        assertThrows(IllegalArgumentException.class,
                () -> service.createBooking(1L, friday, Session.MORNING, 1, request(1)));
    }

    @Test
    void ownershipIsRequired() {
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> service.rescheduleMyBooking(7L, 99L, friday.plusDays(1)));
        verify(resources, never()).findAllByIdWithLock(any());
    }

    @Test
    void derivedStatusesDriveHistoryFilteringAndCapacity() {
        Booking pending = ownedBooking(false);
        Booking confirmed = ownedBooking(true);
        Booking rejected = ownedBooking(false);
        rejected.reject(clock);
        when(bookings.findByUserIdOrderByDateDescIdDesc(1L)).thenReturn(List.of(pending, confirmed, rejected));
        assertEquals(List.of(pending), service.getMyBookingsByStatus(1L, BookingStatus.PENDING));
        assertFalse(rejected.holdsCapacity(clock));
        when(clock.instant()).thenReturn(Session.MORNING.startAt(friday).toInstant());
        assertEquals(List.of(pending), service.getMyBookingsByStatus(1L, BookingStatus.DROPPED));
        assertTrue(service.getMyBookingsByStatus(1L, BookingStatus.PENDING).isEmpty());
        assertFalse(pending.holdsCapacity(clock));
        assertTrue(confirmed.holdsCapacity(clock));
        assertThrows(IllegalStateException.class, () -> pending.confirm(clock));
        when(clock.instant()).thenReturn(Session.MORNING.endAt(friday).toInstant());
        assertEquals(List.of(confirmed), service.getMyBookingsByStatus(1L, BookingStatus.COMPLETED));
        assertTrue(service.getMyBookingsByStatus(1L, BookingStatus.CONFIRMED).isEmpty());
        assertFalse(confirmed.holdsCapacity(clock));
        assertEquals(List.of(rejected), service.getMyBookingsByStatus(1L, BookingStatus.REJECTED));
    }

    @Test
    void rejectedBookingCannotBeRescheduledOrConsumeCapacity() {
        Booking booking = ownedBooking(false);
        booking.reject(clock);
        assertThrows(InvalidBookingStateException.class,
                () -> service.rescheduleMyBooking(7L, 1L, friday.plusDays(1)));
        when(items.findUsage(any(), any(), any())).thenReturn(List.of(
                new ResourceUsage(1L, friday, Session.FULL_DAY, BookingStatus.REJECTED, 10L)));
        assertTrue(service.findAvailability(1, request(1), friday, friday).stream().allMatch(slot -> slot.available()));
    }

    @Test
    void rescheduleValidatesSettingAndNewDateWithoutChangingOriginal() {
        Booking booking = ownedBooking(false);
        assertThrows(IllegalArgumentException.class, () -> service.rescheduleMyBooking(7L, 1L, friday));
        assertThrows(IllegalArgumentException.class, () -> service.rescheduleMyBooking(7L, 1L, friday.plusDays(3)));
        when(settings.findById(Setting.RESCHEDULE_DEADLINE_HOURS)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> service.rescheduleMyBooking(7L, 1L, friday.plusDays(1)));
        for (String value : List.of("-1", "invalid")) {
            when(settings.findById(Setting.RESCHEDULE_DEADLINE_HOURS))
                    .thenReturn(Optional.of(new Setting(Setting.RESCHEDULE_DEADLINE_HOURS, value)));
            assertThrows(IllegalStateException.class, () -> service.rescheduleMyBooking(7L, 1L, friday.plusDays(1)));
        }
        assertEquals(friday, booking.getDate());
    }

    @Test
    void browsingOmitsClosedDaysAndValidatesRangeAndResources() {
        assertEquals(9, service.findAvailability(1, request(1), friday, friday.plusDays(6)).size());
        assertThrows(IllegalArgumentException.class,
                () -> service.findAvailability(1, request(1), friday, friday.minusDays(1)));
        assertThrows(IllegalArgumentException.class,
                () -> service.findAvailability(1, request(1), friday, friday.plusDays(120)));
        when(ticket.isActive()).thenReturn(false);
        assertThrows(IllegalArgumentException.class,
                () -> service.createBooking(1L, friday, Session.MORNING, 1, request(1)));
        when(resources.findAllById(any())).thenReturn(List.of());
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> service.findAvailability(1, request(1), friday, friday));
    }

    private Booking ownedBooking(boolean confirmed) {
        Booking booking = new Booking(user, friday, Session.MORNING, 1);
        booking.addItem(ticket, 1);
        if (confirmed) booking.confirm(clock);
        when(bookings.findByIdAndUserIdWithItems(7L, 1L)).thenReturn(Optional.of(booking));
        return booking;
    }

    private List<BookingItemRequest> request(int quantity) {
        return List.of(new BookingItemRequest(1L, quantity));
    }
}