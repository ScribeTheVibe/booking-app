# booking-app

## Booking service

- The resort opens Friday through Sunday in `Europe/Vilnius`. Session hours are
  currently 09:00–13:00, 13:00–17:00, and 09:00–17:00; these are placeholders in `Session`.
- Requests specify ticket and gear resource IDs and quantities. Ticket quantities
  must total the group size; gear quantities are independent. Full-day bookings
  need capacity in both halves. Browsing returns all future open sessions in an
  inclusive range of up to 120 days, including unavailable options and their price.
- Creation starts pending and snapshots unit prices. Creation and rescheduling
  serialize on resource locks and recheck capacity inside a transaction. Browsing
  is advisory, not a reservation. Customer IDs must come from authentication.
- Rescheduling changes only the date, for pending or confirmed bookings. It must
  occur strictly before the current booking's start minus the global
  `reschedule_deadline_hours` setting. The destination must be a future open
  session. A failed move leaves the original booking unchanged. Existing bookings
  retain their items and price even when a resource is later deactivated or repriced.
- Configure `reschedule_deadline_hours` in `settings` as a nonnegative whole number
  before enabling rescheduling. Missing or invalid values fail explicitly rather
  than silently choosing a deadline. No customer cancellation or payment step exists.

### Lifecycle and future admin integration

`Booking.getStatus()` returns the persisted review decision: `PENDING`, `CONFIRMED`,
or `REJECTED`. Use `Booking.effectiveStatus(clock)` for display: pending bookings
become `DROPPED` at session start, and confirmed bookings become `COMPLETED` at
session end. These time-derived states require no scheduler or database writes;
rejected and dropped bookings remain in history. Only effective pending and
confirmed bookings hold capacity. `BookingService.getMyBookingsByStatus` filters
by this effective state, not just the persisted decision.

Future admin operations should use the injected clock with `confirm(clock)` and
`reject(clock)` inside transactions. Confirmation cannot revive a dropped booking.
Capacity-affecting admin commands must follow the same resource-locking protocol
as customer commands, and handle optimistic-lock conflicts. Admin endpoints are
not implemented yet. Existing database status constraints must allow `REJECTED`;
do not rely on `ddl-auto: update` to migrate old check constraints in production.

### API authentication

For local development, run `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"`
and send `X-Dev-User-Id` with a positive, existing customer ID on every booking request.
Only the booking API bypasses CSRF in this profile. The header is deliberately
spoofable: never enable `dev` on a deployed application.

Without `dev`, the API requires Spring Security authentication and ignores the
development header. Trusted authentication code must resolve the customer account
and install an authenticated token containing `BookingPrincipal(userId)` in the
security context. Login/token verification is not implemented yet; until integrated,
production access fails closed. Generic usernames and generated development users
are not accepted as customer identities. CSRF protection remains enabled for
production writes; session-based clients must supply a valid CSRF token.
Other routes are denied until explicitly configured.

### Tests

Run `.\mvnw.cmd test` on Windows. Service tests use a fixed clock and H2, including
transactional rollback and competing create/reschedule requests. Production
PostgreSQL lock behavior should also be verified before deployment.