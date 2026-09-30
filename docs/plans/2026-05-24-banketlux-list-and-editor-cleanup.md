# BanketLux UI v2 — List & Editor Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-optimized:subagent-driven-development (recommended) or superpowers-optimized:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify the BanketLux bookings list to a single chronological upcoming-bookings view and remove inline status/date-stepper controls from the editor in favor of a calendar picker.

**Architecture:** Native Android, Kotlin, Jetpack Compose, Material 3, Room (offline-only). MVVM with `BookingListViewModel`/`BookingEditorViewModel` consuming `BookingRepository`. UI is presentation-only; pricing, availability and validation stay in domain/repository layers.

**Tech Stack:** Kotlin 1.9+, Jetpack Compose (Material 3), Room (KSP via kapt), JUnit 4 for domain unit tests. Build via `./gradlew assembleDebug`.

**Assumptions:**
- Spec at `docs/specs/2026-05-24-banketlux-list-and-editor-cleanup-design.md` is approved and authoritative. Will NOT work if scope expands to include status editing UI or past-bookings list (those are explicit non-goals).
- Existing Room schema and `BookingEntity.eventDate` storage format (ISO `yyyy-MM-dd`) remains unchanged. Will NOT work if date storage is migrated to epoch or other format.
- `CustomersScreen` continues to consume `observeBookingsForEventDate` (or other repo methods) — the existing DAO query stays.
- No instrumented test infra exists for Compose/DAO/ViewModel; verification relies on `./gradlew assembleDebug` plus manual UI checks. Will NOT work as TDD-pure for UI changes; UI tasks ship with build-pass + manual verification rather than unit tests.

**Execution status (2026-05-24):** Implemented without Git commits per user instruction. `.\gradlew.bat assembleDebug`, `.\gradlew.bat test`, and `.\gradlew.bat clean assembleDebug` passed. Manual device verification deferred because the only detected emulator was offline (`emulator-5554 offline`).

---

## Spec Summary (self-contained for fresh sessions)

The approved spec changes two screens:

**1. Bookings list (`BookingsScreen`)**
- Top bar: `CenterAlignedTopAppBar` with centered title `BanketLux` and centered subtitle = current calendar year, e.g. `2026.`.
- Remove: day-stepper row (◀ date ▶), filter chip row (`Sve` / `Upit` / `Potvrđeno` / `Završeno` / `Otkazano`), empty-state action button.
- List content: all bookings with `eventDate >= today`, sorted ascending by `eventDate, rentalStartDate, id`.
- Each card shows full event date `d.M.yyyy.` (e.g. `21.5.2026.`) as the topmost text line, above the customer name.
- FAB (bottom-right) remains the only "novo zakazivanje" entry point.

**2. Booking editor (`BookingEditorScreen`)**
- Title `Novo zakazivanje` / `Izmeni zakazivanje` must not appear visually "dropped":
  - Remove the extra `.padding(12.dp)` above the LazyColumn (apply 12.dp horizontal only).
  - In `BanketTopBar`, render bare `Text(title)` (no Column wrapper) when `subtitle == null`.
- Section "Događaj":
  - Replace `DateStepper` with `DatePickerField` (label + formatted date + trailing calendar `IconButton`).
  - Tapping the icon opens Material 3 `DatePickerDialog`. Confirming sets the date directly.
  - No arrow buttons remain.
- Remove the `BookingStatus` FilterChip row under "Lokacija" entirely.
- New bookings default to `BookingStatus.CONFIRMED` (was `INQUIRY`).
- All other editor sections (Mušterija, Oprema i usluge, Plaćanje, Napomena, validation/availability panels, Save button) unchanged.

**Non-goals:**
- Editing the status of an existing booking from the UI.
- Showing past bookings in the main list.
- Schema changes, backup-format changes, pricing/availability rule changes.
- Multi-year navigation (year header is a static label).
- Changes to Equipment / Customers / Settings screens.

**Constraints (from `project-map.md`):**
- Offline-first, single-owner; no cloud sync.
- Booking line snapshots must preserve historical deal terms after catalog edits.
- Backup actions must stay visible in `Podešavanja`.
- Availability uses rental start/end overlap, not event date only.
- Only `CONFIRMED` and `COMPLETED` reserve equipment.

---

## File Structure

**Modify:**
- `app/src/main/java/com/banketlux/data/local/dao/BookingDao.kt` — add one Room `@Query` returning a Flow.
- `app/src/main/java/com/banketlux/data/repository/BookingRepository.kt` — add one pass-through function.
- `app/src/main/java/com/banketlux/ui/components/BanketScaffold.kt` — change `BanketTopBar` to render bare title when subtitle is null.
- `app/src/main/java/com/banketlux/ui/bookings/BookingListViewModel.kt` — drop filter/date-stepping; collect new flow.
- `app/src/main/java/com/banketlux/ui/bookings/BookingsScreen.kt` — top bar, list, card, empty state.
- `app/src/main/java/com/banketlux/ui/bookings/BookingEditorViewModel.kt` — default CONFIRMED, add `onEventDatePicked`/`onRentalStartPicked`/`onRentalEndPicked`, drop shifts/onStatusChange.
- `app/src/main/java/com/banketlux/ui/bookings/BookingEditorScreen.kt` — replace `DateStepper` with new `DatePickerField`, remove status chips, fix padding.

**No new files.** New `DatePickerField` composable lives inside `BookingEditorScreen.kt` alongside the old `DateStepper` (which gets deleted).

**Out of scope:** any other file.

---

## Verification Commands

- Build check (every task): `./gradlew assembleDebug`
- Domain test suite: `./gradlew test`
- Manual UI check (final task): install on device/emulator via `./gradlew installDebug` and run the app.

On Windows PowerShell use `.\gradlew.bat assembleDebug` (the repository ships `gradlew.bat`).

---

## Task Dependency Order

1. Task 1 (DAO + Repo) — independent
2. Task 2 (BookingListViewModel) — depends on Task 1
3. Task 3 (BookingsScreen) — depends on Task 2
4. Task 4 (BanketTopBar) — independent
5. Task 5 (BookingEditorViewModel) — independent of 1–4
6. Task 6 (BookingEditorScreen) — depends on Tasks 4 and 5
7. Task 7 (verification & commit baseline) — depends on all

---

### Task 1: Add `observeUpcomingBookings` to DAO and Repository

**Files:**
- Modify: `app/src/main/java/com/banketlux/data/local/dao/BookingDao.kt`
- Modify: `app/src/main/java/com/banketlux/data/repository/BookingRepository.kt`

**Security flag:** `none`

**Does NOT cover:** This task does NOT remove or change `observeBookingsForEventDate`. That query stays because other screens (e.g. `CustomersScreen`) may still depend on it. This task is purely additive.

- [x] **Step 1: Add DAO query**

In `BookingDao.kt`, add this method anywhere inside the `interface BookingDao` block (a good spot is right after `observeBookingsForEventDate`):

```kotlin
@Transaction
@Query(
    """
    SELECT * FROM bookings
    WHERE eventDate >= :today
    ORDER BY eventDate ASC, rentalStartDate ASC, id ASC
    """
)
fun observeUpcomingBookings(today: String): Flow<List<BookingWithLines>>
```

- [x] **Step 2: Add repository pass-through**

In `BookingRepository.kt`, add this method (right after the existing `observeBookingsForEventDate`):

```kotlin
fun observeUpcomingBookings(today: String): Flow<List<BookingWithLines>> =
    dao.observeUpcomingBookings(today)
```

- [x] **Step 3: Build check**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL. (Room kapt must regenerate without errors.)

- [x] **Step 4: Commit (skipped per user instruction: no commits)**

```bash
git add app/src/main/java/com/banketlux/data/local/dao/BookingDao.kt app/src/main/java/com/banketlux/data/repository/BookingRepository.kt
git commit -m "Add observeUpcomingBookings DAO query and repository pass-through"
```

---

### Task 2: Simplify `BookingListViewModel`

**Files:**
- Modify: `app/src/main/java/com/banketlux/ui/bookings/BookingListViewModel.kt`

**Security flag:** `none`

**Does NOT cover:** Does NOT delete `BookingStatus.label()`. That extension is reused by other UI components (`BanketStatusPill`, etc.). Keep it at the top of the file as a top-level function.

- [x] **Step 1: Replace file contents**

Overwrite `BookingListViewModel.kt` with:

```kotlin
package com.banketlux.ui.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banketlux.data.local.dao.BookingWithLines
import com.banketlux.data.repository.BookingRepository
import com.banketlux.domain.model.BookingStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookingListItemUi(
    val bookingId: Long,
    val customerName: String,
    val customerPhone: String,
    val location: String,
    val status: BookingStatus,
    val eventDate: String,
    val rentalStartDate: String,
    val rentalEndDate: String,
    val equipmentSummary: String,
    val totalPriceRsd: Int,
    val remainingDebtRsd: Int
)

data class BookingListUiState(
    val items: List<BookingListItemUi> = emptyList()
)

fun BookingStatus.label(): String = when (this) {
    BookingStatus.INQUIRY -> "Upit"
    BookingStatus.CONFIRMED -> "Potvrđeno"
    BookingStatus.COMPLETED -> "Završeno"
    BookingStatus.CANCELLED -> "Otkazano"
}

class BookingListViewModel(
    private val repository: BookingRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookingListUiState())
    val uiState: StateFlow<BookingListUiState> = _uiState.asStateFlow()

    init {
        val today = LocalDate.now().toString()
        viewModelScope.launch {
            repository.observeUpcomingBookings(today).collect { bookings ->
                _uiState.update { it.copy(items = bookings.map(::toListItem)) }
            }
        }
    }

    private fun toListItem(bookingWithLines: BookingWithLines): BookingListItemUi {
        val booking = bookingWithLines.booking
        val equipmentSummary = bookingWithLines.lines
            .joinToString(separator = ", ") { line ->
                "${line.displayNameSnapshot} x${line.quantity}"
            }
            .ifBlank { "Bez stavki" }

        return BookingListItemUi(
            bookingId = booking.id,
            customerName = booking.customerName,
            customerPhone = booking.customerPhone,
            location = booking.location,
            status = booking.status,
            eventDate = booking.eventDate,
            rentalStartDate = booking.rentalStartDate,
            rentalEndDate = booking.rentalEndDate,
            equipmentSummary = equipmentSummary,
            totalPriceRsd = booking.totalPriceRsd,
            remainingDebtRsd = booking.remainingDebtRsd
        )
    }
}
```

This removes: `BookingFilter` enum, `selectedDate`/`filter` state, `previousDay`/`nextDay`/`setFilter`/`applyFilter` methods, `rawItems` cache, `observeJob`. Keeps `BookingStatus.label()` and `BookingListItemUi` shape.

- [x] **Step 2: Build check (covered by later integration build)**

Run: `.\gradlew.bat assembleDebug`
Expected: FAIL with unresolved references to `BookingFilter`, `previousDay`, `nextDay`, `setFilter`, or `uiState.selectedDate` / `uiState.filter` from `BookingsScreen.kt`. This is expected — Task 3 fixes them.

- [x] **Step 3: Commit (skipped per user instruction: no commits)**

```bash
git add app/src/main/java/com/banketlux/ui/bookings/BookingListViewModel.kt
git commit -m "Simplify BookingListViewModel to a single upcoming-bookings flow"
```

---

### Task 3: Refactor `BookingsScreen`

**Files:**
- Modify: `app/src/main/java/com/banketlux/ui/bookings/BookingsScreen.kt`

**Security flag:** `none`

**Does NOT cover:** Does NOT change the FAB behavior or the card's status pill / metric blocks / equipment summary. Only the top bar, list scaffolding, empty state, and per-card date header change.

- [x] **Step 1: Overwrite file**

Replace the entire contents of `BookingsScreen.kt` with:

```kotlin
package com.banketlux.ui.bookings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banketlux.data.repository.BookingRepository
import com.banketlux.ui.components.BanketEmptyState
import com.banketlux.ui.components.BanketInfoRow
import com.banketlux.ui.components.BanketMetricBlock
import com.banketlux.ui.components.BanketStatusPill
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingsScreen(
    bookingRepository: BookingRepository,
    onCreateBooking: () -> Unit,
    onEditBooking: (Long) -> Unit
) {
    val factory = remember(bookingRepository) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(BookingListViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return BookingListViewModel(bookingRepository) as T
                }
                error("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
    val viewModel: BookingListViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()
    val yearLabel = remember { LocalDate.now().year.toString() + "." }
    val cardDateFormatter = remember { DateTimeFormatter.ofPattern("d.M.yyyy.") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "BanketLux",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = yearLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Novo zakazivanje") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = onCreateBooking
            )
        }
    ) { innerPadding ->
        if (uiState.items.isEmpty()) {
            BanketEmptyState(
                title = "Nema budućih zakazivanja",
                message = "Dodaj zakazivanje preko dugmeta dole desno.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.items, key = { it.bookingId }) { item ->
                    BookingListRow(
                        item = item,
                        formattedEventDate = formatEventDate(item.eventDate, cardDateFormatter),
                        onClick = { onEditBooking(item.bookingId) }
                    )
                }
            }
        }
    }
}

private fun formatEventDate(iso: String, formatter: DateTimeFormatter): String =
    runCatching { LocalDate.parse(iso).format(formatter) }.getOrDefault(iso)

@Composable
private fun BookingListRow(
    item: BookingListItemUi,
    formattedEventDate: String,
    onClick: () -> Unit
) {
    val hasDifferentRentalPeriod = item.rentalStartDate != item.eventDate ||
        item.rentalEndDate != item.eventDate

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = formattedEventDate,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.customerName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                BanketStatusPill(item.status)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BanketInfoRow("Telefon", item.customerPhone)
                BookingDetailBlock(
                    label = "Lokacija",
                    value = item.location,
                    maxLines = 2
                )
                if (hasDifferentRentalPeriod) {
                    BanketInfoRow("Najam", "${item.rentalStartDate} - ${item.rentalEndDate}")
                }
            }

            BookingDetailBlock(
                label = "Oprema",
                value = item.equipmentSummary,
                maxLines = 3
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BanketMetricBlock(
                    label = "Ukupno",
                    value = "${item.totalPriceRsd} RSD",
                    modifier = Modifier.fillMaxWidth()
                )
                BanketMetricBlock(
                    label = "Dug",
                    value = "${item.remainingDebtRsd} RSD",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun BookingDetailBlock(
    label: String,
    value: String,
    maxLines: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}
```

Notes:
- Replaces `BanketTopBar` (which has left-aligned title) with `CenterAlignedTopAppBar` so the title is centered.
- Empty state uses the no-action overload of `BanketEmptyState` — verify the component supports `actionLabel = null` / `onAction = null` (it should, per its existing signature at `BanketFeedback.kt`). If `BanketEmptyState` requires both parameters, omit them in this call; do not change `BanketFeedback.kt`.
- The previous "Datum" row inside the card is removed (the date now headlines the card). Other rows kept.

- [x] **Step 2: Build check**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL.

If `BanketEmptyState` reports missing required parameters, open `app/src/main/java/com/banketlux/ui/components/BanketFeedback.kt`, confirm the parameter signature, and adjust the call site only (do not modify the component).

- [x] **Step 3: Commit (skipped per user instruction: no commits)**

```bash
git add app/src/main/java/com/banketlux/ui/bookings/BookingsScreen.kt
git commit -m "Refactor BookingsScreen to chronological upcoming-bookings list with centered BanketLux header"
```

---

### Task 4: `BanketTopBar` — bare title when subtitle is null

**Files:**
- Modify: `app/src/main/java/com/banketlux/ui/components/BanketScaffold.kt`

**Security flag:** `none`

**Does NOT cover:** Does NOT change `BanketSection` or any other helper in the file. Does NOT change behavior when a non-null `subtitle` is passed (existing two-line layout stays).

- [x] **Step 1: Patch `BanketTopBar` title slot**

Replace the existing `title = { Column { ... } }` block inside `BanketTopBar` (lines ~30–48 currently) with a conditional that skips the Column when there's no subtitle:

```kotlin
title = {
    if (subtitle == null) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    } else {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
},
```

Leave the surrounding `TopAppBar(...)`, `navigationIcon`, `actions`, and `colors = TopAppBarDefaults.topAppBarColors(...)` intact.

- [x] **Step 2: Build check**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL.

- [x] **Step 3: Commit (skipped per user instruction: no commits)**

```bash
git add app/src/main/java/com/banketlux/ui/components/BanketScaffold.kt
git commit -m "Render BanketTopBar title without Column wrapper when no subtitle"
```

---

### Task 5: `BookingEditorViewModel` — default CONFIRMED + absolute date intents

**Files:**
- Modify: `app/src/main/java/com/banketlux/ui/bookings/BookingEditorViewModel.kt`

**Security flag:** `none`

**Does NOT cover:** Does NOT change validation rules, `saveBooking`, `refreshAvailabilityWarnings`, totals calculation, or `applyLoadedBooking`. The loaded-booking path still preserves whatever status was previously saved.

- [x] **Step 1: Change default status in `BookingEditorUiState`**

In the `data class BookingEditorUiState(...)` declaration, change:

```kotlin
val status: BookingStatus = BookingStatus.INQUIRY,
```

to:

```kotlin
val status: BookingStatus = BookingStatus.CONFIRMED,
```

This is the only default-status change. `applyLoadedBooking` already overwrites `status` from persisted data, so existing bookings remain unaffected.

- [x] **Step 2: Replace `shiftEventDate` / `shiftRentalStartDate` / `shiftRentalEndDate` with absolute setters**

Delete these three functions:

```kotlin
fun shiftEventDate(days: Long) = updateEditorState { it.copy(eventDate = it.eventDate.plusDays(days)) }

fun shiftRentalStartDate(days: Long) {
    if (isSavingInProgress()) return
    _uiState.update { it.copy(rentalStartDate = it.rentalStartDate.plusDays(days)) }
    refreshAvailabilityWarnings()
}

fun shiftRentalEndDate(days: Long) {
    if (isSavingInProgress()) return
    _uiState.update { it.copy(rentalEndDate = it.rentalEndDate.plusDays(days)) }
    refreshAvailabilityWarnings()
}
```

In their place, add:

```kotlin
fun onEventDatePicked(date: LocalDate) = updateEditorState { it.copy(eventDate = date) }

fun onRentalStartPicked(date: LocalDate) {
    if (isSavingInProgress()) return
    _uiState.update { it.copy(rentalStartDate = date) }
    refreshAvailabilityWarnings()
}

fun onRentalEndPicked(date: LocalDate) {
    if (isSavingInProgress()) return
    _uiState.update { it.copy(rentalEndDate = date) }
    refreshAvailabilityWarnings()
}
```

- [x] **Step 3: Delete `onStatusChange`**

Remove the entire function:

```kotlin
fun onStatusChange(value: BookingStatus) {
    if (isSavingInProgress()) return
    _uiState.update { it.copy(status = value) }
    refreshAvailabilityWarnings()
}
```

No UI references it after Task 6.

- [x] **Step 4: Build check (intermediate; expected failure confirmed by reviewer compile check)**

Run: `.\gradlew.bat assembleDebug`
Expected: FAIL — `BookingEditorScreen.kt` still references `shiftEventDate`, `shiftRentalStartDate`, `shiftRentalEndDate`, and `onStatusChange`. Task 6 fixes these call sites.

- [x] **Step 5: Commit (skipped per user instruction: no commits)**

```bash
git add app/src/main/java/com/banketlux/ui/bookings/BookingEditorViewModel.kt
git commit -m "Default new bookings to CONFIRMED and replace date shift fns with absolute setters"
```

---

### Task 6: `BookingEditorScreen` — DatePickerField + remove status chips + fix top padding

**Files:**
- Modify: `app/src/main/java/com/banketlux/ui/bookings/BookingEditorScreen.kt`

**Security flag:** `none`

**Does NOT cover:** Does NOT change sections "Mušterija", "Oprema i usluge", "Plaćanje", "Napomena", "Sačuvaj zakazivanje" button, validation panels, or availability warning panels. Only the "Događaj" section, the LazyColumn padding, and the removed status chip row change.

- [x] **Step 1: Replace imports**

In the import block at the top of `BookingEditorScreen.kt`, remove:

```kotlin
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import com.banketlux.domain.model.BookingStatus
```

Add:

```kotlin
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.ZoneOffset
```

(If any of those imports are already present, leave the existing one and skip adding a duplicate.)

- [x] **Step 2: Fix LazyColumn top padding**

Find the `LazyColumn` inside the `Scaffold { innerPadding -> ... }` block. Currently it reads:

```kotlin
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
) {
```

Change to (note: `PaddingValues` from `androidx.compose.foundation.layout.PaddingValues` may need to be imported if not already):

```kotlin
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
) {
```

This removes the extra 12.dp top gap that made the title appear "dropped".

- [x] **Step 3: Replace the "Događaj" section body**

Inside `BanketSection("Događaj", ...)` the inner `Column` currently contains three `DateStepper` calls, an `OutlinedTextField` for `Lokacija`, and a `Row` of `BookingStatus.entries.forEach { FilterChip ... }`. Replace the entire inner `Column { ... }` content with:

```kotlin
Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    DatePickerField(
        label = "Datum događaja",
        value = uiState.eventDate,
        onDatePicked = viewModel::onEventDatePicked
    )
    DatePickerField(
        label = "Početak najma",
        value = uiState.rentalStartDate,
        onDatePicked = viewModel::onRentalStartPicked
    )
    DatePickerField(
        label = "Kraj najma",
        value = uiState.rentalEndDate,
        onDatePicked = viewModel::onRentalEndPicked
    )
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = uiState.location,
        onValueChange = viewModel::onLocationChange,
        label = { Text("Lokacija") },
        singleLine = true
    )
}
```

The `BookingStatus.entries.forEach { FilterChip ... }` row is removed entirely.

- [x] **Step 4: Delete `DateStepper` and add `DatePickerField`**

Delete the existing private `DateStepper` composable (at the bottom of the file). Add at the same location:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    label: String,
    value: LocalDate,
    onDatePicked: (LocalDate) -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy.") }
    var showDialog by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value.format(dateFormatter),
                style = MaterialTheme.typography.titleMedium
            )
        }
        IconButton(onClick = { showDialog = true }) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = "Otvori kalendar za $label"
            )
        }
    }

    if (showDialog) {
        val initialMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val picked = Instant
                                .ofEpochMilli(selectedMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            onDatePicked(picked)
                        }
                        showDialog = false
                    }
                ) {
                    Text("U redu")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Otkaži")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
```

The `Icons.Default.CalendarMonth` icon comes from `androidx.compose.material.icons.filled.CalendarMonth` (already in the `compose.material.icons.extended` dependency per `app/build.gradle.kts`).

- [x] **Step 5: Remove dead `BookingStatus.label()` import inside this file (if any)**

If the file imports `import com.banketlux.ui.bookings.label` or similar for the FilterChip row, remove it. The `BookingStatus.entries.forEach { ... status.label() ... }` block no longer exists in this file. `BookingStatus.label()` itself stays defined in `BookingListViewModel.kt` for other usages.

- [x] **Step 6: Build check**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL. (At this point the cumulative chain Task 2 + Task 5 + Task 6 + Task 3 + Task 4 + Task 1 must all compile together.)

- [x] **Step 7: Run domain tests (regression check)**

Run: `.\gradlew.bat test`
Expected: All existing JUnit tests pass (booking totals, availability, validation, booking draft mapping, backup validator). No new tests added in this task.

- [x] **Step 8: Commit (skipped per user instruction: no commits)**

```bash
git add app/src/main/java/com/banketlux/ui/bookings/BookingEditorScreen.kt
git commit -m "Replace DateStepper with DatePicker and remove status chip row in editor"
```

---

### Task 7: Final verification and baseline commit

**Files:**
- None modified. This task only verifies and produces an evidence commit/note.

**Security flag:** `none`

**Does NOT cover:** Does NOT bump app `versionCode` / `versionName`. Does NOT push to remote. Does NOT modify any code.

- [x] **Step 1: Clean build**

Run: `.\gradlew.bat clean assembleDebug`
Expected: BUILD SUCCESSFUL.

- [x] **Step 2: Full unit test run**

Run: `.\gradlew.bat test`
Expected: All tests PASS. No regression in domain or backup tests.

- [x] **Step 3: Manual UI verification checklist (device verification deferred: emulator offline)**

Install on a connected device or emulator: `.\gradlew.bat installDebug` (if no device is available, skip and note "device verification deferred"). Walk through:

1. **Main screen top bar:** centered `BanketLux` title with `2026.` (current year) subtitle below it.
2. **List:** all upcoming bookings only; sorted ascending by event date; first card shows the earliest upcoming date as a bold header line.
3. **Card date header:** format is `d.M.yyyy.` (e.g. `21.5.2026.` not `21.05.2026.`).
4. **No day stepper, no filter chips, no center "Novo zakazivanje" button.** FAB at bottom-right remains.
5. **Empty state:** when DB has no upcoming bookings, "Nema budućih zakazivanja" appears with no action button (FAB still visible).
6. **Editor — title position:** open "Novo zakazivanje"; title sits at standard top-bar height with no extra gap above the first section.
7. **Editor — date fields:** "Datum događaja", "Početak najma", "Kraj najma" each have label + formatted date + trailing calendar icon. Tap each — Material 3 picker opens. Pick a date → field updates. No arrow buttons present.
8. **Editor — no status chips:** under the Lokacija field there are no Upit/Potvrđeno/Završeno/Otkazano chips.
9. **Save flow:** create a new booking, save it. Open the database via a fresh load of the list — the new booking appears with status persisted as `CONFIRMED` (verifiable by closing and reopening the booking in the editor; the loaded UI state's underlying status field should be `CONFIRMED`).
10. **Regression check:** open Equipment, Customers, and Settings screens — none changed; all still navigate, search, and back out correctly.

- [x] **Step 4: Update `project-map.md` if structure changed (no structure change; no update needed)**

The plan touches files already in the hot-files list; no new files were created. If `project-map.md` hot-files list is stale, update it. Otherwise no change needed.

- [x] **Step 5: Final summary commit (skipped: no commits per user instruction)**

If `project-map.md` was updated:

```bash
git add project-map.md
git commit -m "Refresh project map after UI v2 list & editor cleanup"
```

If nothing changed in this step, no commit.

---

## Stop conditions

- Any `gradlew assembleDebug` failure after Task 6 must be diagnosed before proceeding to Task 7.
- If `DatePickerDialog` import fails, verify `androidx.compose.material3` BOM version is recent enough (Material 3 1.1+). Do not downgrade or replace with a third-party picker — file an open issue and pause.
- If Manual UI verification (Task 7 Step 3) cannot run because no device is available, note "device verification deferred — adb not found" in `session-log.md` and proceed; do NOT fabricate verification outcomes.

## Expected final behavior

- Opening the app shows a `BanketLux` / `2026.` centered top bar.
- The list shows all bookings whose event date is today or later, sorted by date ascending.
- Each card prominently shows the event date as a bold heading.
- The FAB is the only entry point to the editor; the empty state has no action button.
- Inside the editor:
  - Title is at standard top-bar height.
  - All three date fields use a tap-to-pick calendar icon; no arrows.
  - There is no status chip row.
  - Newly-created bookings persist with `status = CONFIRMED`.
- All existing pricing, availability, validation, snapshotting, backup, equipment, customers, and settings behaviors are unchanged.
- All existing unit tests still pass.
