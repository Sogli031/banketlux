# BanketLux — List & Editor Cleanup (UI v2)

_Approved: 2026-05-24_

## Scope

UI-only cleanup of the bookings list (`BookingsScreen`) and the booking editor (`BookingEditorScreen`). The list switches from a single-day view with filter chips to a chronological list of all upcoming bookings. The editor loses inline status chips and per-arrow date stepping in favor of a single calendar picker per date field. Top-bar treatment is simplified across both screens.

No changes to pricing, availability, snapshotting, persistence, backup, or repository contracts beyond a single additive DAO/repository query for upcoming bookings.

## Non-goals

- Editing the status of an existing booking from the UI (status field remains in the model and database; current UI does not expose a way to change it after creation).
- Showing past bookings in the main list. Past bookings remain reachable via the Customers screen.
- Changing pricing, snapshotting, availability rules, validation, backup schema, or repository behavior for any non-list query.
- Changes to Equipment, Customers, or Settings screens.
- Multi-year navigation. The header shows the current calendar year as static label.

## Behavior changes

### Bookings list (`BookingsScreen`)

- **Top bar:** `BanketLux` as title, centered. Subtitle (also centered): current calendar year, e.g. `2026.`. Use `CenterAlignedTopAppBar`.
- **Removed UI:**
  - Day-stepper row (`◀ date ▶`).
  - Filter chip row (`Sve`, `Upit`, `Potvrđeno`, `Završeno`, `Otkazano`).
  - Empty-state action button "Novo zakazivanje" (FAB remains as the only entry point).
- **List content:** all bookings with `eventDate >= today`, sorted ascending by `eventDate` (ties broken by `rentalStartDate`, then `id`).
- **Card content:** each row shows the full event date (`d.M.yyyy.` — e.g. `21.5.2026.`) as the topmost line of the card, above the customer name. Other card fields (phone, location, equipment, totals, debt, status pill) remain unchanged.
- **Empty state:** message "Nema budućih zakazivanja" with no action button. FAB stays visible.
- **FAB:** unchanged — bottom-right `ExtendedFloatingActionButton` opens the editor.

### Booking editor (`BookingEditorScreen`)

- **Top bar spacing:** title `Novo zakazivanje` / `Izmeni zakazivanje` must sit at the standard Material 3 top-bar height. Fix the perceived "drop" by:
  - Removing the extra `.padding(12.dp)` above the LazyColumn content (apply 12.dp horizontally only; vertical padding stays inside list items via `verticalArrangement.spacedBy(12.dp)`).
  - In `BanketTopBar`, render bare `Text(title)` when `subtitle == null` (no `Column` wrapper) to avoid implicit vertical layout shift.
- **Section "Događaj":**
  - All three date fields (`Datum događaja`, `Početak najma`, `Kraj najma`) lose the `◀`/`▶` arrows.
  - Each field renders as a single row: label + formatted date + trailing calendar icon button.
  - Tapping the calendar icon opens a Material 3 `DatePickerDialog`. Confirming sets the date directly (replaces previous value).
- **Status chips:** the `FilterChip` row under "Lokacija" is removed entirely.
- **Default status for new bookings:** `BookingStatus.CONFIRMED`. (Existing bookings retain whatever status they were saved with.)
- All other editor sections (Mušterija, Oprema i usluge, Plaćanje, Napomena, validation/availability panels, save button) remain unchanged.

## Architecture & data flow

### New repository / DAO query

Add an additive query for upcoming bookings sorted ascending:

```kotlin
// BookingDao
@Query("""
    SELECT * FROM bookings
    WHERE eventDate >= :today
    ORDER BY eventDate ASC, rentalStartDate ASC, id ASC
""")
fun observeUpcomingBookings(today: String): Flow<List<BookingWithLines>>

// BookingRepository
fun observeUpcomingBookings(today: String): Flow<List<BookingWithLines>> =
    dao.observeUpcomingBookings(today)
```

The `today` parameter is `LocalDate.now().toString()` (ISO `yyyy-MM-dd`), matching the existing storage convention used by `observeBookingsForEventDate`.

### `BookingListViewModel`

- Drop: `BookingFilter` enum, `selectedDate`, `filter`, `previousDay`, `nextDay`, `setFilter`, `applyFilter`, `rawItems` cache.
- New state: `data class BookingListUiState(val items: List<BookingListItemUi> = emptyList())`.
- On init: collect `repository.observeUpcomingBookings(LocalDate.now().toString())`, map to `BookingListItemUi`, push into state.
- Mapping unchanged.

### `BookingEditorViewModel`

- Default `status = BookingStatus.CONFIRMED` in the initial UI state for new bookings (was `INQUIRY`).
- Add three new intents that set a date absolutely:
  - `onEventDatePicked(date: LocalDate)`
  - `onRentalStartPicked(date: LocalDate)`
  - `onRentalEndPicked(date: LocalDate)`
- Existing `shiftEventDate` / `shiftRentalStartDate` / `shiftRentalEndDate` are removed (no longer called from UI).
- Existing `onStatusChange` is removed from UI callers; the function itself may remain in the ViewModel as dead code or be deleted (delete to keep code clean).

### Compose surface changes

| File | Change |
|---|---|
| `ui/bookings/BookingsScreen.kt` | Replace `Scaffold`+top bar+day stepper+filter row with `CenterAlignedTopAppBar` (title "BanketLux", subtitle year). Remove day stepper Row and filter Row. Card layout: add event date as first text line. Empty state: drop `actionLabel`/`onAction`. |
| `ui/bookings/BookingListViewModel.kt` | Simplify state, drop filter/date stepping. New query call. |
| `ui/bookings/BookingEditorScreen.kt` | Replace `DateStepper` calls with new `DatePickerField`. Remove status `FilterChip` row. Remove extra top padding on LazyColumn. |
| `ui/bookings/BookingEditorViewModel.kt` | Default to CONFIRMED. Add three `onXPicked` intents. Drop shift/onStatusChange callers. |
| `ui/components/BanketScaffold.kt` | `BanketTopBar` renders bare `Text(title)` when subtitle is null (no Column wrapper). |
| `data/local/dao/BookingDao.kt` | Add `observeUpcomingBookings(today)` query. |
| `data/repository/BookingRepository.kt` | Add `observeUpcomingBookings(today)`. |

A new private composable `DatePickerField(label, value, onDatePicked)` lives in `BookingEditorScreen.kt` (replacing `DateStepper`). It renders the field row and owns the `rememberSaveable` boolean that toggles a `DatePickerDialog`.

## Interfaces / contracts

- `BookingDao.observeUpcomingBookings(today: String): Flow<List<BookingWithLines>>` — pure additive; existing `observeBookingsForEventDate` stays for `CustomersScreen` history use.
- `BookingRepository.observeUpcomingBookings(today: String)` — thin pass-through.
- `BookingEditorViewModel.onEventDatePicked(LocalDate)` etc. — set the date and trigger the same downstream recomputation paths the shift functions used (totals, availability check).
- No public API changes outside these additions/removals.

## Error handling

- DatePicker dialog: confirm callback returns `LocalDate?`. If null (user dismisses), do nothing. If non-null, write to state.
- Repository query: same `Flow` error semantics as existing queries; nothing new.
- Today's date is recomputed only when ViewModel initializes; if the user keeps the app open across midnight, yesterday's same-day bookings continue to be shown until the screen is recomposed. Acceptable for v1.

## Testing strategy

- **Unit (Kotlin):**
  - `BookingDao` test: insert bookings on `today - 1`, `today`, `today + 1`; verify `observeUpcomingBookings(today)` excludes the past one and orders the rest ascending.
  - `BookingListViewModel` test: emit a list from a fake repository; assert state mapping; assert no filtering by status.
  - `BookingEditorViewModel` test: new editor instance has `status == CONFIRMED`; `onEventDatePicked` updates state and triggers recompute.
- **Manual / visual:**
  - Top bar centered "BanketLux" / "2026." on bookings screen.
  - Card shows date `d.M.yyyy.` as first line.
  - Empty state has no action button.
  - Editor opens with title near the top (no extra gap above first section).
  - Tapping the calendar icon on each of the three date fields opens a picker; confirming updates the visible date.
  - No status chips visible in editor; new save lands in DB with `CONFIRMED`.

## Rollout / migration

- No schema migration. No database version bump.
- Backup format unchanged.
- Existing bookings with `status = INQUIRY` continue to render their status pill in the list card; they just cannot be re-edited to a different status from this UI. This is documented as a non-goal.

## Failure-mode check (resolved)

1. **All new bookings reserve equipment immediately (status=CONFIRMED).** Accepted; explicit user request to remove status UI.
2. **No UI path to cancel/complete an existing booking.** Accepted non-goal; status field remains in the model for future expansion.
3. **Past bookings disappear from main list.** Accepted; Customers screen still surfaces per-customer history.
4. **Rental end can be picked before rental start.** Existing editor validation already surfaces this via `BanketAlertPanel`; behavior unchanged.
5. **Midnight rollover keeps showing today's bookings.** Minor; acceptable until next screen recomposition.
