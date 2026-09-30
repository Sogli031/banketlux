package com.banketlux.data.repository

import com.banketlux.data.catalog.StandardPriceList
import com.banketlux.data.local.dao.EquipmentDao
import com.banketlux.data.local.entity.EquipmentEntity
import kotlinx.coroutines.flow.Flow

data class PriceListSeedResult(val added: Int, val skipped: Int)

class EquipmentRepository(private val dao: EquipmentDao) {
    fun observeAllEquipment(): Flow<List<EquipmentEntity>> = dao.observeAllEquipment()

    fun observeActiveEquipment(): Flow<List<EquipmentEntity>> = dao.observeActiveEquipment()

    suspend fun save(item: EquipmentEntity): Long = dao.upsert(item)

    suspend fun deactivate(id: Long) = dao.deactivate(id)

    suspend fun activate(id: Long) = dao.activate(id)

    suspend fun getById(id: Long): EquipmentEntity? = dao.getById(id)

    /**
     * Dodaje stavke standardnog cenovnika koje još ne postoje u katalogu.
     * Postojeće stavke se ne diraju — ručno izmenjene cene ostaju kakve jesu.
     */
    suspend fun seedStandardPriceList(): PriceListSeedResult {
        val existingNames = dao.getAllForBackup()
            .map { it.name.trim().lowercase() }
            .toSet()
        val missing = StandardPriceList.items()
            .filterNot { it.name.trim().lowercase() in existingNames }
        if (missing.isNotEmpty()) {
            dao.insertAll(missing)
        }
        return PriceListSeedResult(
            added = missing.size,
            skipped = StandardPriceList.items().size - missing.size
        )
    }
}
