package com.programaorcamentos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class BudgetArchiveRow(
    val id: Long,
    val budgetNumber: String,
    val clientName: String,
    val issueDateEpochDay: Long,
    val totalIncludingVatCents: Long,
    val status: BudgetStatus,
    val updatedAtMillis: Long
)

data class BudgetWithClientAndLines(
    @androidx.room.Embedded val budget: Budget,
    @androidx.room.Relation(parentColumn = "clientId", entityColumn = "id") val client: Client,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "budgetId") val lines: List<BudgetLine>
)

@Dao
interface BudgetDao {
    @Query("SELECT * FROM CompanyProfile WHERE id = 1")
    fun observeCompany(): Flow<CompanyProfile?>

    @Query("SELECT * FROM CompanyProfile WHERE id = 1")
    suspend fun getCompany(): CompanyProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCompany(profile: CompanyProfile)

    @Insert
    suspend fun insertClient(client: Client): Long

    @Update
    suspend fun updateClient(client: Client)

    @Insert
    suspend fun insertBudget(budget: Budget): Long

    @Update
    suspend fun updateBudget(budget: Budget)

    @Insert
    suspend fun insertLines(lines: List<BudgetLine>)

    @Query("DELETE FROM BudgetLine WHERE budgetId = :budgetId")
    suspend fun deleteLinesForBudget(budgetId: Long)

    @Query("DELETE FROM Budget WHERE id = :budgetId")
    suspend fun deleteBudget(budgetId: Long)

    @Transaction
    @Query("SELECT * FROM Budget WHERE id = :budgetId")
    suspend fun getBudget(budgetId: Long): BudgetWithClientAndLines?

    @Transaction
    @Query("SELECT * FROM Budget ORDER BY updatedAtMillis DESC")
    suspend fun getAllBudgets(): List<BudgetWithClientAndLines>

    @Query(
        """
        SELECT Budget.id, Budget.budgetNumber, Client.name AS clientName, Budget.issueDateEpochDay,
               Budget.totalIncludingVatCents, Budget.status, Budget.updatedAtMillis
        FROM Budget INNER JOIN Client ON Client.id = Budget.clientId
        WHERE (:query = '' OR Budget.budgetNumber LIKE '%' || :query || '%' OR Client.name LIKE '%' || :query || '%')
          AND (:status IS NULL OR Budget.status = :status)
        ORDER BY Budget.updatedAtMillis DESC
        """
    )
    fun observeArchive(query: String, status: BudgetStatus?): Flow<List<BudgetArchiveRow>>

    @Query("DELETE FROM BudgetLine")
    suspend fun clearLines()

    @Query("DELETE FROM Budget")
    suspend fun clearBudgets()

    @Query("DELETE FROM Client")
    suspend fun clearClients()

    @Query("DELETE FROM CompanyProfile")
    suspend fun clearCompany()
}
