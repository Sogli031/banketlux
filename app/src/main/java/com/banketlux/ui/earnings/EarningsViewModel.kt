package com.banketlux.ui.earnings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banketlux.data.local.entity.EarningEntity
import com.banketlux.data.repository.EarningsRepository
import com.banketlux.domain.earnings.EarningsOverview
import com.banketlux.domain.earnings.summarizeEarnings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EarningsUiState(
    val overview: EarningsOverview = EarningsOverview(0, 0, emptyList()),
    val entries: List<EarningEntity> = emptyList(),
    val expandedYears: Set<Int> = emptySet(),
    /** Otvoreni meseci kao "godina-mesec". */
    val expandedMonths: Set<String> = emptySet()
)

class EarningsViewModel(
    private val repository: EarningsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(EarningsUiState())
    val uiState: StateFlow<EarningsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Pokupi sve što je u međuvremenu isteklo, pa tek onda prikaži zaradu.
            runCatching { repository.archiveEndedBookings() }

            repository.observeEarnings().collect { entries ->
                val overview = summarizeEarnings(entries)
                _uiState.update { current ->
                    current.copy(
                        overview = overview,
                        entries = entries,
                        // Najskorija godina je otvorena da se odmah vide meseci.
                        expandedYears = current.expandedYears.ifEmpty {
                            setOfNotNull(overview.years.firstOrNull()?.year)
                        }
                    )
                }
            }
        }
    }

    fun toggleYear(year: Int) {
        _uiState.update { current ->
            current.copy(
                expandedYears = if (year in current.expandedYears) {
                    current.expandedYears - year
                } else {
                    current.expandedYears + year
                }
            )
        }
    }

    fun toggleMonth(key: String) {
        _uiState.update { current ->
            current.copy(
                expandedMonths = if (key in current.expandedMonths) {
                    current.expandedMonths - key
                } else {
                    current.expandedMonths + key
                }
            )
        }
    }

    fun deleteEarning(id: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.deleteEarning(id) }
                .onSuccess { onDeleted() }
        }
    }
}
