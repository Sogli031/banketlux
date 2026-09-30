# Project Map
_Generated: 2026-05-24 10:59 | Git: initialized-no-commits_

## Directory Structure
./ - BanketLux Android workspace root with docs, project memory files, and Gradle project.
app/ - Android application module.
app/src/main/java/com/banketlux/domain/ - Pure business rules (pricing, validation, date overlap, booking status semantics).
app/src/main/java/com/banketlux/data/local/ - Room database, entities, DAOs, and type converters.
app/src/main/java/com/banketlux/data/repository/ - Repository boundary used by ViewModels.
app/src/main/java/com/banketlux/data/backup/ - Local backup DTO/validator/import-export implementation.
app/src/main/java/com/banketlux/ui/ - Compose UI by feature (bookings, equipment, customers, settings, navigation, theme).
app/src/main/java/com/banketlux/ui/components/ - Shared Compose presentation components for BanketLux top bars, sections, empty/alert panels, info rows, metric blocks, and status pills.
app/src/test/java/com/banketlux/ - Unit tests for domain and backup validation/mapping contracts.
docs/specs/ - Approved product/design scope.
docs/plans/ - Executable implementation plan with task checkboxes.

## Key Files
logo.jpg - Source branding asset copied into the Android app drawable set.
app/src/main/java/com/banketlux/BanketLuxApp.kt - Root navigation shell; wires repositories and feature screens.
app/src/main/java/com/banketlux/data/local/BanketLuxDatabase.kt - Room database definition and DAO registry.
app/src/main/java/com/banketlux/domain/availability/AvailabilityCalculator.kt - Core overlap/shortage rule for reserving statuses.
app/src/main/java/com/banketlux/data/repository/BookingRepository.kt - Booking persistence facade, including overlap query path with current-booking exclusion.
app/src/main/java/com/banketlux/ui/bookings/BookingEditorViewModel.kt - Booking editor orchestration: validation, totals, snapshot mapping, availability warnings.
app/src/main/java/com/banketlux/ui/equipment/EquipmentViewModel.kt - Repository-backed equipment CRUD flow and validation.
app/src/main/java/com/banketlux/ui/customers/CustomersViewModel.kt - Search and selected-customer history flow.
app/src/main/java/com/banketlux/data/backup/BackupRepository.kt - JSON export/import with pre-validation and transactional replace.
app/src/main/java/com/banketlux/ui/settings/SettingsScreen.kt - Visible backup export/import entry point using Android file pickers.
app/src/main/java/com/banketlux/ui/components/ - Presentation-only component layer used by the Operational Pro UI redesign.
docs/specs/2026-05-24-banketlux-initial-design.md - Approved Version 1 product specification.
docs/specs/2026-05-24-banketlux-operational-pro-ui-design.md - Approved UI redesign specification.
docs/plans/2026-05-24-banketlux-android-app.md - Execution checklist for scaffold/domain/data/UI/backup/polish tasks.
docs/plans/2026-05-24-banketlux-operational-pro-ui.md - Executed Operational Pro UI checklist and verification record.
project-map.md - Persistent orientation document for future sessions.
session-log.md - Durable decision history across sessions.

## Critical Constraints
- Offline-first, single-owner Android app; no cloud sync or multi-user auth in Version 1.
- Availability must use rental start/end overlap, not event date only.
- Only `CONFIRMED` and `COMPLETED` bookings reserve equipment for shortage calculations.
- Booking line snapshots (`displayNameSnapshot`, `unitPriceSnapshotRsd`) must preserve historical deal terms after catalog edits.
- Overbooking shows warning but does not hard-block save.
- Backup import must validate before any destructive write and replace data only inside one transaction.
- Backup actions must stay visible in `Podešavanja` (not hidden behind overflow).
- Operational Pro redesign is UI-only; money, availability, validation, persistence, backup schema, and repository behavior remain out of scope for UI polish.

## Hot Files
app/src/main/java/com/banketlux/BanketLuxApp.kt, app/src/main/java/com/banketlux/ui/components/, app/src/main/java/com/banketlux/ui/bookings/BookingsScreen.kt, app/src/main/java/com/banketlux/ui/bookings/BookingEditorScreen.kt, app/src/main/java/com/banketlux/ui/bookings/BookingLineEditor.kt, app/src/main/java/com/banketlux/ui/equipment/EquipmentScreen.kt, app/src/main/java/com/banketlux/ui/equipment/EquipmentEditorDialog.kt, app/src/main/java/com/banketlux/ui/customers/CustomersScreen.kt, app/src/main/java/com/banketlux/ui/settings/SettingsScreen.kt, app/src/main/java/com/banketlux/data/backup/BackupRepository.kt, docs/plans/2026-05-24-banketlux-operational-pro-ui.md, project-map.md, session-log.md
