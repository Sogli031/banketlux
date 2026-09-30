package com.banketlux.domain.model

/**
 * Pranje stolnjaka uz šatru. Nije oprema iz kataloga — čuva se kao posebna stavka
 * zakazivanja sa ovim id-em, pa ulazi u zbir, kalendar, zaradu i backup bez nove kolone.
 */
object TableclothWashing {
    const val EQUIPMENT_ID = -1L
    const val NAME = "Pranje stolnjaka"
    const val DEFAULT_PRICE_RSD = 2000
    const val TENT_CATEGORY = "Šatre"
}
