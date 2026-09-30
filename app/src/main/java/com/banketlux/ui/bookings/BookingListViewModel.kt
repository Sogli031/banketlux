package com.banketlux.ui.bookings

import com.banketlux.data.local.entity.label
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banketlux.data.local.dao.BookingWithLines
import com.banketlux.data.repository.BookingRepository
import com.banketlux.domain.model.BookingStatus
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
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
    val equipmentLines: List<String>,
    val totalPriceRsd: Int
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

    // Današnji datum kao stanje: da lista ne ostane na jučerašnjem danu
    // ako aplikacija prenoći otvorena.
    private val today = MutableStateFlow(LocalDate.now().toString())

    init {
        viewModelScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            today.flatMapLatest { repository.observeUpcomingBookings(it) }
                .collect { bookings ->
                    _uiState.update { it.copy(items = bookings.map(::toListItem)) }
                }
        }
    }

    /** Poziva se kad se ekran ponovo prikaže, da se granica "danas" pomeri. */
    fun refreshToday() {
        today.value = LocalDate.now().toString()
    }

    private fun toListItem(bookingWithLines: BookingWithLines): BookingListItemUi {
        val booking = bookingWithLines.booking
        // Svaka stavka u svom redu — nabrajanje zarezima se na telefonu slabo čita.
        val equipmentLines = bookingWithLines.lines.map { it.label() }

        return BookingListItemUi(
            bookingId = booking.id,
            customerName = booking.customerName,
            customerPhone = booking.customerPhone,
            location = booking.location,
            status = booking.status,
            eventDate = booking.eventDate,
            rentalStartDate = booking.rentalStartDate,
            rentalEndDate = booking.rentalEndDate,
            equipmentLines = equipmentLines,
            totalPriceRsd = booking.totalPriceRsd
        )
    }
}
