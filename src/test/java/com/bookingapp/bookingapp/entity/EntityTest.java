package com.bookingapp.bookingapp.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EntityTest {

    @Test
    void settingEqualsAndHashCodeWorkInCollections() {
        Setting s1 = new Setting("reschedule_deadline_hours", "24");
        Setting s2 = new Setting("reschedule_deadline_hours", "48");
        Setting s3 = new Setting("different_key", "24");

        assertEquals(s1, s2);
        assertNotEquals(s1, s3);
        assertEquals(s1.hashCode(), s2.hashCode());

        Set<Setting> set = new HashSet<>();
        set.add(s1);
        assertTrue(set.contains(s2));
        assertFalse(set.contains(s3));
    }

    @Test
    void userLifecycleAndEquality() {
        User user1 = new User("John", "Doe", "hash123", "john@example.com", "+1234567890");
        user1.onCreate();
        assertNotNull(user1.getCreatedAt());
        assertNull(user1.getUpdatedAt());

        user1.onUpdate();
        assertNotNull(user1.getUpdatedAt());

        User user2 = new User("Jane", "Doe", "hash456", "jane@example.com", "+1987654321");
        // Prior to persistence (null ID), instances are only reference-equal
        assertNotEquals(user1, user2);
    }

    @Test
    void bookableResourceCreationAndConstraints() {
        BookableResource resource = new BookableResource("Ski Pass", "All day ski pass",
                ResourceType.TICKET, new BigDecimal("45.00"), 100);

        assertEquals("Ski Pass", resource.getName());
        assertEquals(ResourceType.TICKET, resource.getType());
        assertEquals(new BigDecimal("45.00"), resource.getUnitPrice());
        assertEquals(100, resource.getTotalCapacity());
        assertTrue(resource.isActive());

        resource.onCreate();
        assertNotNull(resource.getCreatedAt());

        resource.onUpdate();
        assertNotNull(resource.getUpdatedAt());
    }

    @Test
    void bookingValidationOnPersistAndUpdate() {
        User user = new User("Alice", "Smith", "hash", "alice@example.com", "+1122334455");
        LocalDate nextFriday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
        Booking booking = new Booking(user, nextFriday, Session.MORNING, 2);

        BookableResource ticket = new BookableResource("Morning Ticket", "Desc",
                ResourceType.TICKET, new BigDecimal("30.00"), 50);

        // Before adding items, validation should fail
        assertThrows(IllegalStateException.class, booking::onCreate);
        assertThrows(IllegalStateException.class, booking::onUpdate);

        // Add 2 tickets to match numberOfPeople = 2
        booking.addItem(ticket, 2);

        assertDoesNotThrow(booking::onCreate);
        assertDoesNotThrow(booking::onUpdate);
        assertNotNull(booking.getCreatedAt());
        assertNotNull(booking.getUpdatedAt());

        // Unmodifiable items list
        assertEquals(1, booking.getItems().size());
        assertThrows(UnsupportedOperationException.class, () -> booking.getItems().clear());
    }

    @Test
    void bookingItemSubtotalCalculation() {
        User user = new User("Bob", "Brown", "hash", "bob@example.com", "+1223344556");
        LocalDate nextSaturday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
        Booking booking = new Booking(user, nextSaturday, Session.EVENING, 3);

        BookableResource ticket = new BookableResource("Pass", "Desc",
                ResourceType.TICKET, new BigDecimal("25.00"), 50);
        booking.addItem(ticket, 3);

        BookingItem item = booking.getItems().getFirst();
        assertEquals(new BigDecimal("75.00"), item.subtotal());
        assertEquals(new BigDecimal("75.00"), booking.totalPrice());
    }
}
