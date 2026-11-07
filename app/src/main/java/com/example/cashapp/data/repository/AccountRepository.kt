package com.example.cashapp.data.repository

import androidx.room.withTransaction
import com.example.cashapp.data.local.CashDatabase
import com.example.cashapp.data.local.entity.AccountEntity
import com.example.cashapp.data.local.model.AccountWithBalance
import kotlinx.coroutines.flow.Flow

/**
 * Számlák kezelése.
 *
 * Szabályok:
 * - Ha a számlára már hivatkozik tétel, nem törölhető (csak archiválható), és a pénzneme nem váltható
 *   – különben a régi tételek összegei értelmüket vesztenék.
 * - Az archivált számla eltűnik a választókból és a főoldali egyenlegből, de visszaállítható.
 */
class AccountRepository(private val db: CashDatabase) {

    private val accountDao = db.accountDao()

    fun observeActiveWithBalance(): Flow<List<AccountWithBalance>> = accountDao.observeActiveWithBalance()

    fun observeAllWithBalance(): Flow<List<AccountWithBalance>> = accountDao.observeAllWithBalance()

    fun observeWithBalance(id: Long): Flow<AccountWithBalance?> = accountDao.observeWithBalance(id)

    fun observeActive(): Flow<List<AccountEntity>> = accountDao.observeActive()

    suspend fun get(id: Long): AccountEntity? = accountDao.getById(id)

    /** Van-e már tétel / szabály a számlán. */
    suspend fun isUsed(id: Long): Boolean = accountDao.usageCount(id) > 0

    data class AccountDraft(
        val id: Long?,
        val name: String,
        val currencyCode: String,
        val initialBalanceMinor: Long,
        val color: Long,
        val icon: String,
    )

    suspend fun save(draft: AccountDraft): Long = db.withTransaction {
        if (draft.id == null) {
            accountDao.insert(
                AccountEntity(
                    name = draft.name,
                    currencyCode = draft.currencyCode,
                    initialBalanceMinor = draft.initialBalanceMinor,
                    color = draft.color,
                    icon = draft.icon,
                    sortOrder = accountDao.maxSortOrder() + 1,
                )
            )
        } else {
            val existing = checkNotNull(accountDao.getById(draft.id)) { "Nincs ilyen számla: ${draft.id}" }
            val currency = if (existing.currencyCode != draft.currencyCode && accountDao.usageCount(draft.id) > 0) {
                existing.currencyCode // használt számla pénzneme nem változhat
            } else draft.currencyCode
            accountDao.update(
                existing.copy(
                    name = draft.name,
                    currencyCode = currency,
                    initialBalanceMinor = draft.initialBalanceMinor,
                    color = draft.color,
                    icon = draft.icon,
                )
            )
            draft.id
        }
    }

    /** @return igaz, ha végleg törölve; hamis, ha használatban volt, ezért csak archiválva. */
    suspend fun delete(id: Long): Boolean = db.withTransaction {
        val account = accountDao.getById(id) ?: return@withTransaction true
        if (accountDao.usageCount(id) > 0) {
            accountDao.update(account.copy(isArchived = true))
            false
        } else {
            accountDao.delete(account)
            true
        }
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        accountDao.getById(id)?.let { accountDao.update(it.copy(isArchived = archived)) }
    }

    /** Aktív számlák új sorrendje. */
    suspend fun reorder(orderedIds: List<Long>) = db.withTransaction {
        val byId = accountDao.getActive().associateBy { it.id }
        accountDao.updateAll(orderedIds.mapIndexedNotNull { i, id -> byId[id]?.copy(sortOrder = i) })
    }
}
