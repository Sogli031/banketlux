package com.banketlux.data.calendar

import com.banketlux.data.local.entity.label
import android.content.Context
import android.util.Log
import com.banketlux.data.local.dao.BookingWithLines
import com.banketlux.ui.components.formatRsd
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "GoogleCalendarSync"
private const val PREFS_NAME = "banketlux_calendar_prefs"
private const val KEY_ENABLED = "calendar_sync_enabled"
private const val NETWORK_TIMEOUT_MS = 30_000L
private const val PRIMARY_CALENDAR = "primary"

private const val KEY_STATUS_TIME = "calendar_status_time"
private const val KEY_STATUS_OK = "calendar_status_ok"
private const val KEY_STATUS_MESSAGE = "calendar_status_message"

/**
 * Verzija izgleda događaja. Kad se promeni (naslov, podsetnici), postojeći događaji
 * se jednom prepišu — vidi [GoogleCalendarSync.refreshEventsIfFormatChanged].
 */
private const val EVENT_FORMAT_VERSION = 3
private const val KEY_EVENT_FORMAT = "calendar_event_format"

data class CalendarStatus(val timeMillis: Long, val success: Boolean, val message: String)

/** Događaj iz kalendara koji može da se uveze kao zakazivanje — samo naziv i datumi. */
data class CalendarImportCandidate(
    val eventId: String,
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate
)

/** Koliko unapred se gledaju događaji za uvoz. */
private const val IMPORT_YEARS_AHEAD = 2L

/** Greška Google API-ja prevedena u nešto što korisnik može da popravi. */
internal fun describeCalendarError(error: Throwable): String {
    if (error is UserRecoverableAuthIOException) {
        return "Google traži ponovnu potvrdu pristupa — odjavi se pa se prijavi ponovo."
    }
    if (error is GoogleJsonResponseException) {
        val reason = error.details?.errors?.firstOrNull()?.reason.orEmpty()
        val detail = error.details?.message ?: error.statusMessage.orEmpty()
        return when {
            reason == "accessNotConfigured" || detail.contains("has not been used", true) ||
                detail.contains("disabled", true) ->
                "Google Calendar API nije uključen u Google Cloud projektu aplikacije."
            error.statusCode == 401 -> "Prijava je istekla — odjavi se pa se prijavi ponovo."
            error.statusCode == 403 && reason.contains("insufficient", true) ->
                "Nalog nema dozvolu za kalendar — odjavi se pa se prijavi ponovo."
            error.statusCode == 403 -> "Google je odbio upis (403): $detail"
            else -> "Google greška ${error.statusCode}: $detail"
        }
    }
    if (error is java.io.IOException) return "Mrežna greška: ${error.message ?: error.javaClass.simpleName}"
    return error.message ?: error.javaClass.simpleName
}

/**
 * Upisuje zakazivanja u glavni Google kalendar prijavljenog naloga.
 * Sinhronizacija je jednosmerna: aplikacija → kalendar. Izmene napravljene
 * direktno u kalendaru se ne vraćaju nazad u aplikaciju.
 */
class GoogleCalendarSync(private val context: Context) {

    /**
     * Upisi u kalendar idu jedan po jedan. Bez ovoga pojedinačni upis i naknadni upis svih
     * zakazivanja mogu istovremeno da vide prazan `calendarEventId` istog zakazivanja i
     * oba naprave događaj. Zaključava se ceo ciklus: čitanje → upis u Google → čuvanje id-a.
     */
    private val writeMutex = Mutex()

    suspend fun <T> withWriteLock(block: suspend () -> T): T = writeMutex.withLock { block() }

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    init {
        _enabled.value = prefs.getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * Dozvola za kalendar se traži pri prijavi. Nalozi prijavljeni pre nego što je
     * ova funkcija dodata je nemaju — takav korisnik mora ponovo da se prijavi.
     */
    fun hasCalendarPermission(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(account, Scope(CalendarScopes.CALENDAR_EVENTS))
    }

    fun isReady(): Boolean = _enabled.value && hasCalendarPermission()

    /**
     * Ishod poslednjeg pokušaja upisa, da se u Podešavanjima vidi ZAŠTO kalendar ne radi —
     * workeri inače greške samo loguju, a korisnik ne vidi ništa.
     */
    fun lastStatus(): CalendarStatus? {
        val time = prefs.getLong(KEY_STATUS_TIME, 0L).takeIf { it > 0 } ?: return null
        return CalendarStatus(
            timeMillis = time,
            success = prefs.getBoolean(KEY_STATUS_OK, false),
            message = prefs.getString(KEY_STATUS_MESSAGE, null).orEmpty()
        )
    }

    fun recordStatus(success: Boolean, message: String) {
        prefs.edit()
            .putLong(KEY_STATUS_TIME, System.currentTimeMillis())
            .putBoolean(KEY_STATUS_OK, success)
            .putString(KEY_STATUS_MESSAGE, message)
            .apply()
    }

    /** Posle promene izgleda događaja jednom prepiše sve postojeće događaje u kalendaru. */
    fun refreshEventsIfFormatChanged() {
        if (prefs.getInt(KEY_EVENT_FORMAT, 1) >= EVENT_FORMAT_VERSION) return
        if (isReady()) CalendarSyncScheduler.backfillAll(context)
        prefs.edit().putInt(KEY_EVENT_FORMAT, EVENT_FORMAT_VERSION).apply()
    }

    /** Razlog zašto upis ne može ni da počne, ili null ako je sve spremno. */
    fun notReadyReason(): String? = when {
        !_enabled.value -> "Upis u kalendar je isključen."
        GoogleSignIn.getLastSignedInAccount(context) == null -> "Nisi prijavljen Google nalogom."
        !hasCalendarPermission() -> "Nalog nema dozvolu za kalendar — odjavi se pa se prijavi ponovo."
        else -> null
    }

    /**
     * Pravi ili ažurira događaj za zakazivanje. Vraća id događaja koji treba
     * sačuvati uz zakazivanje, da bi sledeća izmena pogodila isti događaj.
     */
    suspend fun upsertEvent(
        bookingWithLines: BookingWithLines,
        existingEventId: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext Result.failure(
            IllegalStateException("Nema dozvole za Google kalendar.")
        )

        runCatching {
            val event = buildEvent(bookingWithLines)
            // Blokirajući poziv tajmaut ne može da prekine — rezultat se hvata u promenljivu,
            // da uspešan (samo spor) upis ne bude prijavljen kao greška. Inače bi retry
            // napravio duplikat događaja u kalendaru.
            var saved: Event? = null
            withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                saved = if (existingEventId.isNullOrBlank()) {
                    service.events().insert(PRIMARY_CALENDAR, event).execute()
                } else {
                    // Ako je događaj u međuvremenu obrisan iz kalendara, napravi nov.
                    // Samo tada — druga greška (dozvola, mreža) bi inače pravila duplikate.
                    try {
                        service.events().update(PRIMARY_CALENDAR, existingEventId, event).execute()
                    } catch (e: GoogleJsonResponseException) {
                        if (e.statusCode != 404 && e.statusCode != 410) throw e
                        Log.w(TAG, "Događaj $existingEventId ne postoji, pravim nov")
                        service.events().insert(PRIMARY_CALENDAR, event).execute()
                    }
                }
            }
            val result = saved ?: error("Isteklo vreme — spora mreža.")
            requireNotNull(result.id) { "Kalendar nije vratio id događaja." }
        }.recoverCatching {
            Log.e(TAG, "Upis u kalendar nije uspeo", it)
            throw IllegalStateException(describeCalendarError(it), it)
        }
    }

    /**
     * Budući događaji iz glavnog kalendara (i oni koji su u toku), za uvoz u aplikaciju.
     * Rođendani, odsustva i slični posebni događaji se preskaču.
     */
    suspend fun listUpcomingEvents(
        today: LocalDate = LocalDate.now()
    ): Result<List<CalendarImportCandidate>> = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext Result.failure(
            IllegalStateException("Nema dozvole za Google kalendar.")
        )
        val zone = ZoneId.systemDefault()
        runCatching {
            var events: List<Event>? = null
            withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                val collected = mutableListOf<Event>()
                var pageToken: String? = null
                do {
                    val page = service.events().list(PRIMARY_CALENDAR)
                        .setTimeMin(DateTime(today.atStartOfDay(zone).toInstant().toEpochMilli()))
                        .setTimeMax(
                            DateTime(
                                today.plusYears(IMPORT_YEARS_AHEAD).atStartOfDay(zone)
                                    .toInstant().toEpochMilli()
                            )
                        )
                        .setSingleEvents(true)
                        .setOrderBy("startTime")
                        .setMaxResults(250)
                        .setPageToken(pageToken)
                        .execute()
                    collected += page.items.orEmpty()
                    pageToken = page.nextPageToken
                } while (pageToken != null)
                events = collected
            }
            val result = events ?: error("Isteklo vreme — spora mreža.")
            result.mapNotNull { event -> toImportCandidate(event, zone, today) }
        }.recoverCatching {
            Log.e(TAG, "Čitanje kalendara nije uspelo", it)
            throw IllegalStateException(describeCalendarError(it), it)
        }
    }

    suspend fun deleteEvent(eventId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext Result.failure(
            IllegalStateException("Nema dozvole za Google kalendar.")
        )
        runCatching {
            var deleted = false
            withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                service.events().delete(PRIMARY_CALENDAR, eventId).execute()
                deleted = true
            }
            if (!deleted) error("Isteklo vreme — spora mreža.")
            Unit
        }.recoverCatching { error ->
            // Već obrisan iz kalendara — nema šta da se radi. Svaka druga greška (mreža,
            // prijava) mora da se ponovi, inače događaj zauvek ostaje u kalendaru.
            if (error is GoogleJsonResponseException &&
                (error.statusCode == 404 || error.statusCode == 410)
            ) {
                Log.d(TAG, "Događaj $eventId je već obrisan iz kalendara")
                Unit
            } else {
                Log.w(TAG, "Brisanje događaja $eventId nije uspelo: ${error.message}")
                throw IllegalStateException(describeCalendarError(error), error)
            }
        }
    }

    private fun buildService(): Calendar? {
        val account: GoogleSignInAccount = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        if (!GoogleSignIn.hasPermissions(account, Scope(CalendarScopes.CALENDAR_EVENTS))) return null

        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(CalendarScopes.CALENDAR_EVENTS)
        )
        credential.selectedAccount = account.account

        return Calendar.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("BanketLux")
            .build()
    }
}

/** Naslov, opis i trajanje događaja izdvojeni da mogu da se testiraju bez mreže. */
internal fun buildEventSummary(bookingWithLines: BookingWithLines): String {
    val name = bookingWithLines.booking.customerName.trim().ifEmpty { "Zakazivanje" }
    val equipment = bookingWithLines.lines.joinToString(", ") { it.label() }
    return if (equipment.isEmpty()) name else "$name: $equipment"
}

internal fun buildEventDescription(bookingWithLines: BookingWithLines): String {
    val booking = bookingWithLines.booking
    return buildList {
        if (booking.customerPhone.isNotBlank()) add("Telefon: ${booking.customerPhone}")
        if (bookingWithLines.lines.isNotEmpty()) {
            add("")
            add("Oprema:")
            bookingWithLines.lines.forEach { line ->
                add("• ${line.label()}")
            }
        }
        add("")
        add("Ukupno: ${formatRsd(booking.totalPriceRsd)}")
        if (booking.notes.isNotBlank()) {
            add("")
            add("Napomena: ${booking.notes}")
        }
    }.joinToString("\n")
}

/**
 * Google tretira kraj celodnevnog događaja kao ekskluzivan, pa se poslednji
 * dan najma mora pomeriti za jedan dan unapred da bi bio uključen.
 */
internal fun exclusiveEndDate(rentalEndDate: String, rentalStartDate: String): String {
    val end = runCatching { LocalDate.parse(rentalEndDate) }.getOrNull()
        ?: runCatching { LocalDate.parse(rentalStartDate) }.getOrNull()
        ?: LocalDate.now()
    return end.plusDays(1).toString()
}

/**
 * Datumi događaja za uvoz. Celodnevni događaj ima ekskluzivan kraj (dan posle poslednjeg),
 * a događaj sa satnicom koji se završava tačno u ponoć ne zauzima i sledeći dan.
 */
internal fun importDates(
    allDayStart: String?,
    allDayEnd: String?,
    startMillis: Long?,
    endMillis: Long?,
    zone: ZoneId
): Pair<LocalDate, LocalDate>? {
    val start = allDayStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: startMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        ?: return null
    val end = when {
        allDayEnd != null -> runCatching { LocalDate.parse(allDayEnd).minusDays(1) }.getOrNull()
        endMillis != null -> Instant.ofEpochMilli(endMillis - 1).atZone(zone).toLocalDate()
        else -> null
    } ?: start
    return start to (if (end.isBefore(start)) start else end)
}

private fun toImportCandidate(event: Event, zone: ZoneId, today: LocalDate): CalendarImportCandidate? {
    val id = event.id ?: return null
    if (event.status == "cancelled") return null
    // Rođendani, odsustva, vreme za fokus... nisu poslovi.
    if (event.eventType != null && event.eventType != "default") return null
    val (start, end) = importDates(
        allDayStart = event.start?.date?.toStringRfc3339(),
        allDayEnd = event.end?.date?.toStringRfc3339(),
        startMillis = event.start?.dateTime?.value,
        endMillis = event.end?.dateTime?.value,
        zone = zone
    ) ?: return null
    if (end.isBefore(today)) return null
    return CalendarImportCandidate(
        eventId = id,
        title = event.summary?.trim().orEmpty(),
        startDate = start,
        endDate = end
    )
}

private fun buildEvent(bookingWithLines: BookingWithLines): Event {
    val booking = bookingWithLines.booking
    return Event().apply {
        summary = buildEventSummary(bookingWithLines)
        location = booking.location.ifBlank { null }
        description = buildEventDescription(bookingWithLines)
        start = EventDateTime().setDate(DateTime(booking.rentalStartDate))
        end = EventDateTime().setDate(
            DateTime(exclusiveEndDate(booking.rentalEndDate, booking.rentalStartDate))
        )
        // Bez podsetnika u kalendaru — obaveštenje dan ranije šalje sama aplikacija.
        reminders = Event.Reminders().apply {
            useDefault = false
            overrides = emptyList()
        }
    }
}
