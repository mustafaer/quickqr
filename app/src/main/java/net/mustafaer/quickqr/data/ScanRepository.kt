package net.mustafaer.quickqr.data

import kotlinx.coroutines.flow.Flow
import net.mustafaer.quickqr.utils.ScanResultType

/** Scan history, as the ViewModels see it. Room is one implementation; tests use a fake. */
interface ScanRepository {
    val scans: Flow<List<ScanEntity>>
    suspend fun record(text: String, type: ScanResultType)
    suspend fun delete(ids: Collection<Int>)
    suspend fun clear()
    suspend fun setPinned(id: Int, pinned: Boolean)
}

class RoomScanRepository(private val dao: ScanDao) : ScanRepository {

    override val scans: Flow<List<ScanEntity>> = dao.getAllScans()

    override suspend fun record(text: String, type: ScanResultType) {
        dao.insertAndCap(ScanEntity(text = text, type = type.typeStr), AppDatabase.HISTORY_LIMIT)
    }

    override suspend fun delete(ids: Collection<Int>) {
        if (ids.isNotEmpty()) dao.deleteScans(ids.toList())
    }

    override suspend fun clear() {
        dao.clearAllScans()
    }

    override suspend fun setPinned(id: Int, pinned: Boolean) {
        dao.setPinned(id, pinned)
    }
}
