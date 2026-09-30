package com.banketlux.domain.model

enum class BookingStatus(val reservesEquipment: Boolean) {
    INQUIRY(false),
    CONFIRMED(true),
    COMPLETED(true),
    CANCELLED(false)
}
