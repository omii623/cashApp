package com.example.cashapp.data.repository

import androidx.room.withTransaction
import com.example.cashapp.data.local.CashDatabase
import com.example.cashapp.data.local.entity.LoanEntity
import com.example.cashapp.data.local.entity.TransactionEntity
import com.example.cashapp.data.local.model.LoanWithBalance
import com.example.cashapp.data.local.model.TransactionListItem
import com.example.cashapp.domain.loan.LoanMath
import com.example.cashapp.domain.model.LoanOpeningPart
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Magánkölcsönök. Minden pénzmozgás egy rendes bevétel/kiadás tétel a kiválasztott számlán
 * (loanId-val): a számla egyenlegében és a bevétel/kiadás kimutatásokban is szerepel.
 * Egy mozgás bármilyen pénznemben, bármelyik számlára / számláról történhet (mint a bevétel/kiadás);
 * a hátralék a [TransactionEntity.loanAmountMinor]-ból, a kölcsön pénznemében számol.
 */
class LoanRepository(private val db: CashDatabase) {

    private val loanDao = db.loanDao()
    private val transactionDao = db.transactionDao()
    private val accountDao = db.accountDao()

    fun observeAll(): Flow<List<LoanWithBalance>> = loanDao.observeAll()

    fun observe(id: Long): Flow<LoanWithBalance?> = loanDao.observe(id)

    fun observeMovements(loanId: Long): Flow<List<TransactionListItem>> = transactionDao.observeForLoan(loanId)

    suspend fun get(id: Long): LoanEntity? = loanDao.getById(id)

    /** Van-e már mozgása (ha igen, az irány és a pénznem nem módosítható). */
    suspend fun hasMovements(id: Long): Boolean = loanDao.movementCount(id) > 0

    /**
     * A nyitó adatok könyvelése: ha be van kapcsolva, a kölcsön összege / a már visszafizetett rész
     * valódi tételként kerül a [accountId] számlára a kezdés dátumával (bevételként / kiadásként).
     */
    data class OpeningBooking(
        val bookAmount: Boolean = false,
        val bookRepaid: Boolean = false,
        val accountId: Long? = null,
    )

    fun observeOpenings(loanId: Long): Flow<List<TransactionListItem>> = transactionDao.observeLoanOpenings(loanId)

    suspend fun getOpenings(loanId: Long): List<TransactionEntity> = transactionDao.getLoanOpenings(loanId)

    /** Új kölcsön vagy meglévő módosítása. Mozgás után az irány és a pénznem nem változhat. */
    suspend fun save(loan: LoanEntity, booking: OpeningBooking = OpeningBooking()): Long = db.withTransaction {
        require(loan.personName.isNotBlank()) { "Adj meg nevet" }
        require(loan.openingAmountMinor >= 0) { "A kölcsön összege nem lehet negatív" }
        require(loan.openingRepaidMinor in 0..loan.openingAmountMinor) { "A visszafizetett rész nem lehet több a kölcsönnél" }
        val existing = loan.id.takeIf { it != 0L }?.let { loanDao.getById(it) }
        val id = if (existing == null) {
            loanDao.insert(loan.copy(id = 0))
        } else {
            if (loanDao.movementCount(existing.id) > 0) {
                require(existing.direction == loan.direction && existing.currencyCode == loan.currencyCode) {
                    "Mozgás után az irány és a pénznem nem módosítható"
                }
            }
            // A lezárást a szerkesztő nem kezeli → marad, ami volt.
            loanDao.update(loan.copy(createdAt = existing.createdAt, closedDate = existing.closedDate))
            existing.id
        }
        syncOpenings(id, loan, booking)
        id
    }

    /** A nyitó adatokból könyvelt tételek létrehozása / frissítése / törlése a beállítás szerint. */
    private suspend fun syncOpenings(loanId: Long, loan: LoanEntity, booking: OpeningBooking) {
        val current = transactionDao.getLoanOpenings(loanId).associateBy { it.loanOpening }
        val wanted = mapOf(
            LoanOpeningPart.AMOUNT to (loan.openingAmountMinor.takeIf { booking.bookAmount && it > 0 }),
            LoanOpeningPart.REPAID to (loan.openingRepaidMinor.takeIf { booking.bookRepaid && it > 0 }),
        )
        val account = if (wanted.values.any { it != null }) {
            val acc = checkNotNull(booking.accountId?.let { accountDao.getById(it) }) { "Válassz számlát" }
            require(acc.currencyCode == loan.currencyCode) { "A számla pénzneme eltér a kölcsönétől" }
            acc
        } else null
        for ((part, amount) in wanted) {
            val existingTx = current[part]
            if (amount == null || account == null) {
                existingTx?.let { transactionDao.deleteById(it.id) }
                continue
            }
            val entity = TransactionEntity(
                type = LoanMath.transactionType(loan.direction, increase = part == LoanOpeningPart.AMOUNT),
                date = loan.startDate,
                amountMinor = amount,
                currencyCode = loan.currencyCode,
                accountId = account.id,
                accountAmountMinor = amount,
                loanId = loanId,
                loanOpening = part,
                loanAmountMinor = amount,
            )
            if (existingTx == null) transactionDao.insert(entity)
            else transactionDao.update(entity.copy(id = existingTx.id, createdAt = existingTx.createdAt))
        }
    }

    /**
     * Lezárás akkor is, ha nincs teljesen visszafizetve: a maradék hátralék leíródik
     * (a listában „Lezárva”, az összesítőkből kimarad). Számlát nem mozgat; újranyitható.
     */
    suspend fun close(id: Long, date: LocalDate = LocalDate.now()) = loanDao.setClosed(id, date)

    suspend fun reopen(id: Long) = loanDao.setClosed(id, null)

    /** A kölcsön és az összes mozgása törlődik (a számlák egyenlege ennek megfelelően visszaáll). */
    suspend fun delete(id: Long) = db.withTransaction {
        loanDao.deleteMovements(id)
        loanDao.deleteById(id)
    }

    /**
     * Mozgás adatai az űrlapról. [increase]: új kölcsön (nő a tartozás), különben törlesztés.
     * - [amountMinor] / [currencyCode]: a beírt összeg és pénzneme,
     * - [accountAmountMinor]: ugyanez a számla pénznemében ([rateToAccount] árfolyammal, ha eltér),
     * - [loanAmountMinor]: ugyanez a kölcsön pénznemében (ennyivel változik a hátralék).
     */
    data class MovementDraft(
        val increase: Boolean,
        val amountMinor: Long,
        val accountId: Long,
        val date: LocalDate,
        val note: String,
        val currencyCode: String? = null,
        val accountAmountMinor: Long = amountMinor,
        val rateToAccount: Double? = null,
        val loanAmountMinor: Long = amountMinor,
    )

    /**
     * Mozgás rögzítése vagy módosítása ([transactionId] != null).
     * Törlesztés lehet több is a hátraléknál: ilyenkor a kölcsön rendezett lesz (a hátralék 0).
     * @throws IllegalArgumentException érvénytelen adatnál
     */
    suspend fun saveMovement(loanId: Long, draft: MovementDraft, transactionId: Long? = null): Long = db.withTransaction {
        require(draft.amountMinor > 0 && draft.accountAmountMinor > 0 && draft.loanAmountMinor > 0) { "Az összeg legyen pozitív" }
        val loan = checkNotNull(loanDao.getById(loanId)) { "Nincs ilyen kölcsön: $loanId" }
        val account = checkNotNull(accountDao.getById(draft.accountId)) { "Nincs ilyen számla" }
        val currencyCode = draft.currencyCode ?: account.currencyCode

        val original = transactionId?.let { checkNotNull(transactionDao.getById(it)) { "Nincs ilyen tétel: $it" } }
        require(original == null || (original.loanId == loanId && original.loanOpening == null)) { "A tétel nem ehhez a kölcsönhöz tartozik" }

        val entity = TransactionEntity(
            type = LoanMath.transactionType(loan.direction, draft.increase),
            date = draft.date,
            amountMinor = draft.amountMinor,
            currencyCode = currencyCode,
            accountId = account.id,
            accountAmountMinor = draft.accountAmountMinor,
            rateToAccount = draft.rateToAccount,
            note = draft.note.trim(),
            loanId = loanId,
            loanAmountMinor = draft.loanAmountMinor,
        )
        if (original == null) {
            transactionDao.insert(entity)
        } else {
            transactionDao.update(entity.copy(id = original.id, createdAt = original.createdAt))
            original.id
        }
    }

    suspend fun getMovement(transactionId: Long): TransactionEntity? =
        transactionDao.getById(transactionId)?.takeIf { it.loanId != null && it.loanOpening == null }

    suspend fun deleteMovement(transactionId: Long) = transactionDao.deleteById(transactionId)
}
