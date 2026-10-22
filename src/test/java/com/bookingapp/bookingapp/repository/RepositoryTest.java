package com.bookingapp.bookingapp.repository;

import com.bookingapp.bookingapp.dto.ResourceUsage;
import com.bookingapp.bookingapp.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class RepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookableResourceRepository bookableResourceRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingItemRepository bookingItemRepository;

    @Autowired
    private SettingRepository settingRepository;

    private User savedUser;
    private BookableResource passResource;
    private BookableResource equipmentResource;

    @BeforeEach
    void setUp() {
        savedUser = userRepository.save(new User("Test", "User", "hash123", "test.user@example.com", "+37060000000"));

        passResource = bookableResourceRepository.save(
                new BookableResource("Morning Pass", "Pass for morning session",
                        ResourceType.TICKET, new BigDecimal("25.00"), 50));

        equipmentResource = bookableResourceRepository.save(
                new BookableResource("Ski Equipment", "Complete ski set",
                        ResourceType.GEAR, new BigDecimal("15.00"), 30));
    }

    @Test
    void userRepositoryQueriesAndCaseInsensitivity() {
        Optional<User> direct = userRepository.findByEmail("test.user@example.com");
        assertTrue(direct.isPresent());
        assertEquals(savedUser.getId(), direct.get().getId());

        Optional<User> mixedCase = userRepository.findByEmailIgnoreCase("TeSt.UsEr@ExAmPlE.cOm");
        assertTrue(mixedCase.isPresent());
        assertEquals(savedUser.getId(), mixedCase.get().getId());

        assertTrue(userRepository.existsByEmail("test.user@example.com"));
        assertTrue(userRepository.existsByEmailIgnoreCase("TEST.USER@EXAMPLE.COM"));
        assertFalse(userRepository.existsByEmailIgnoreCase("nonexistent@example.com"));

        assertTrue(userRepository.existsByPhone("+37060000000"));
        assertFalse(userRepository.existsByPhone("+37069999999"));

        Optional<User> byPhone = userRepository.findByPhone("+37060000000");
        assertTrue(byPhone.isPresent());
        assertEquals(savedUser.getId(), byPhone.get().getId());
    }

    @Test
    void bookableResourceRepositoryOrderingAndLocking() {
        List<BookableResource> active = bookableResourceRepository.findByActiveTrueOrderByTypeAscNameAsc();
        assertFalse(active.isEmpty());

        List<BookableResource> locked = bookableResourceRepository.findAllByIdWithLock(
                List.of(equipmentResource.getId(), passResource.getId()));
        assertEquals(2, locked.size());
        // Verify deterministic ascending ID order
        assertTrue(locked.get(0).getId() < locked.get(1).getId());
    }

    @Test
    void bookingRepositoryAndItemRepositoryUsageAggregation() {
        LocalDate bookingDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
        Booking booking = new Booking(savedUser, bookingDate, Session.MORNING);
        booking.addItem(passResource, 2);
        booking.addItem(equipmentResource, 1);
        Booking savedBooking = bookingRepository.save(booking);

        assertNotNull(savedBooking.getId());

        // Test BookingRepository queries
        Optional<Booking> withItems = bookingRepository.findByIdAndUserIdWithItems(savedBooking.getId(), savedUser.getId());
        assertTrue(withItems.isPresent());
        assertEquals(2, withItems.get().getItems().size());

        List<Booking> userBookings = bookingRepository.findByUserIdOrderByDateDescIdDesc(savedUser.getId());
        assertEquals(1, userBookings.size());

        // Test BookingItemRepository usage aggregation
        List<ResourceUsage> usage = bookingItemRepository.findUsage(
                Set.of(passResource.getId(), equipmentResource.getId()),
                bookingDate, bookingDate);
        assertEquals(2, usage.size());

        long totalQuantity = usage.stream().mapToLong(ResourceUsage::quantity).sum();
        assertEquals(3, totalQuantity);

        // Test findUsageExcludingBooking
        List<ResourceUsage> excludedUsage = bookingItemRepository.findUsageExcludingBooking(
                Set.of(passResource.getId(), equipmentResource.getId()),
                bookingDate, bookingDate, savedBooking.getId());
        assertTrue(excludedUsage.isEmpty());
    }

    @Test
    void settingRepositoryLookup() {
        Setting setting = new Setting(Setting.RESCHEDULE_DEADLINE_HOURS, "48");
        settingRepository.save(setting);

        Optional<Setting> retrieved = settingRepository.findById(Setting.RESCHEDULE_DEADLINE_HOURS);
        assertTrue(retrieved.isPresent());
        assertEquals("48", retrieved.get().getSettingValue());
        assertEquals(48, retrieved.get().asInt());
    }
}
