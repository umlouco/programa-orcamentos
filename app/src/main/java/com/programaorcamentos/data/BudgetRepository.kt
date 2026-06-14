package com.programaorcamentos.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class EditableClient(
    val name: String = "",
    val vatNumber: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val notes: String = ""
)

data class EditableLine(
    val id: Long = 0,
    val description: String = "",
    val quantity: String = "1",
    val unit: String = "un",
    val unitPrice: String = "0.00",
    val vatRate: String = "23"
)

data class EditableBudget(
    val id: Long = 0,
    val budgetNumber: String = "",
    val issueDate: LocalDate = LocalDate.now(),
    val expiryDate: LocalDate = LocalDate.now().plusDays(30),
    val projectTitle: String = "",
    val projectLocation: String = "",
    val status: BudgetStatus = BudgetStatus.Draft,
    val notes: String = "",
    val pricesEnteredWithVat: Boolean = false,
    val exemptFromVat: Boolean = false,
    val client: EditableClient = EditableClient(),
    val lines: List<EditableLine> = listOf(EditableLine()),
)

class BudgetRepository(context: Context, private val database: AppDatabase) {
    private val appContext = context.applicationContext
    private val dao = database.dao()
    private val settings = SettingsStore(context)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun observeCompany(): Flow<CompanyProfile?> = dao.observeCompany()

    suspend fun getCompany(): CompanyProfile? = dao.getCompany()

    suspend fun saveCompany(profile: CompanyProfile) = dao.saveCompany(profile)

    fun observeArchive(query: String, status: BudgetStatus?): Flow<List<BudgetArchiveRow>> =
        dao.observeArchive(query, status)

    suspend fun newBudgetDraft(): EditableBudget {
        val company = getCompany()
        val sequence = settings.reserveBudgetNumber()
        val now = LocalDate.now()
        return EditableBudget(
            budgetNumber = "ORC-${now.year}-${sequence.toString().padStart(4, '0')}",
            expiryDate = now.plusDays((company?.defaultValidityDays ?: 30).toLong()),
            lines = listOf(EditableLine(vatRate = (company?.defaultVatRate ?: 23.0).toString().trimEnd('.', '0')))
        )
    }

    suspend fun getEditableBudget(id: Long): EditableBudget? {
        val full = dao.getBudget(id) ?: return null
        return full.toEditable()
    }

    suspend fun getFullBudget(id: Long): BudgetWithClientAndLines? = dao.getBudget(id)

    suspend fun saveBudgetTransactional(editable: EditableBudget): Long {
        val now = System.currentTimeMillis()
        val cleanLines = editable.lines.filter { it.description.isNotBlank() }
        val calculated = cleanLines.mapIndexed { index, line ->
            val quantity = line.quantity.replace(",", ".").toDoubleOrNull() ?: 0.0
            val vatRate = line.vatRate.replace(",", ".").toDoubleOrNull() ?: 0.0
            val effectiveVatRate = if (editable.exemptFromVat) 0.0 else vatRate
            val totals = calculateLine(quantity, parseMoneyToCents(line.unitPrice), effectiveVatRate, editable.pricesEnteredWithVat)
            Triple(index, line.copy(quantity = quantity.toString(), vatRate = vatRate.toString()), totals)
        }
        val subtotal = calculated.sumOf { it.third.subtotalExcludingVatCents }
        val vat = calculated.sumOf { it.third.vatAmountCents }
        val total = calculated.sumOf { it.third.totalIncludingVatCents }

        return database.withTransaction {
            val existing = if (editable.id == 0L) null else dao.getBudget(editable.id)
            val client = Client(
                id = existing?.client?.id ?: 0,
                name = editable.client.name.ifBlank { "Cliente" },
                vatNumber = editable.client.vatNumber,
                address = editable.client.address,
                phone = editable.client.phone,
                email = editable.client.email,
                notes = editable.client.notes
            )
            val clientId = if (client.id == 0L) dao.insertClient(client) else {
                dao.updateClient(client)
                client.id
            }
            val budget = Budget(
                id = editable.id,
                budgetNumber = editable.budgetNumber.ifBlank { newBudgetDraft().budgetNumber },
                clientId = clientId,
                issueDateEpochDay = editable.issueDate.toEpochDay(),
                expiryDateEpochDay = editable.expiryDate.toEpochDay(),
                projectTitle = editable.projectTitle,
                projectLocation = editable.projectLocation,
                status = editable.status,
                notes = editable.notes,
                pricesEnteredWithVat = editable.pricesEnteredWithVat,
                exemptFromVat = editable.exemptFromVat,
                subtotalExcludingVatCents = subtotal,
                vatTotalCents = vat,
                totalIncludingVatCents = total,
                createdAtMillis = existing?.budget?.createdAtMillis ?: now,
                updatedAtMillis = now
            )
            val budgetId = if (budget.id == 0L) dao.insertBudget(budget) else {
                dao.updateBudget(budget)
                budget.id
            }
            dao.deleteLinesForBudget(budgetId)
            dao.insertLines(calculated.map { (index, line, totals) ->
                BudgetLine(
                    budgetId = budgetId,
                    position = index,
                    description = line.description,
                    quantity = line.quantity.toDoubleOrNull() ?: 0.0,
                    unit = line.unit,
                    unitPriceExcludingVatCents = totals.unitPriceExcludingVatCents,
                    vatRate = line.vatRate.toDoubleOrNull() ?: 0.0,
                    subtotalExcludingVatCents = totals.subtotalExcludingVatCents,
                    vatAmountCents = totals.vatAmountCents,
                    totalIncludingVatCents = totals.totalIncludingVatCents
                )
            })
            budgetId
        }
    }

    suspend fun duplicateBudget(id: Long): Long {
        val original = getEditableBudget(id) ?: return 0
        val draft = newBudgetDraft()
        return saveBudgetTransactional(original.copy(id = 0, budgetNumber = draft.budgetNumber, status = BudgetStatus.Draft))
    }

    suspend fun deleteBudget(id: Long) = dao.deleteBudget(id)

    data class ImportResult(val imported: Int, val skipped: Int, val total: Int)

    suspend fun createBackupZip(): File {
        val dir = File(appContext.cacheDir, "backups").also { it.mkdirs() }
        val file = File(dir, "Orcamentos_Backup_${LocalDate.now()}.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(createBackupJson().toByteArray())
            zip.closeEntry()
        }
        return file
    }

    private suspend fun createBackupJson(): String {
        val backup = BackupFile(
            nextBudgetNumber = settings.peekNextBudgetNumber(),
            company = dao.getCompany(),
            budgets = dao.getAllBudgets().map { BudgetBackup(it.budget, it.client, it.lines.sortedBy { line -> line.position }) }
        )
        settings.markBackupToday()
        return json.encodeToString(backup)
    }

    suspend fun importFromZip(inputStream: InputStream): ImportResult {
        val raw = extractJsonFromZip(inputStream)
            ?: extractJsonFromStream(inputStream)
            ?: throw IllegalArgumentException("Ficheiro de backup inválido")
        val backup = previewBackup(raw)
        require(backup.format == "programa-orcamentos" && backup.version == 1) { "Ficheiro de backup inválido" }
        writeSafetyBackup()
        var imported = 0
        var skipped = 0
        database.withTransaction {
            if (backup.company != null && dao.getCompany() == null) {
                dao.saveCompany(backup.company)
            }
            for (item in backup.budgets) {
                if (dao.getBudgetByNumber(item.budget.budgetNumber) != null) {
                    skipped++
                    continue
                }
                val existingClient = dao.getClientByName(item.client.name)
                val clientId = if (existingClient != null) {
                    existingClient.id
                } else {
                    dao.insertClient(item.client.copy(id = 0))
                }
                val budgetId = dao.insertBudget(item.budget.copy(id = 0, clientId = clientId))
                dao.insertLines(item.lines.map { it.copy(id = 0, budgetId = budgetId) })
                imported++
            }
        }
        return ImportResult(imported = imported, skipped = skipped, total = backup.budgets.size)
    }

    private fun extractJsonFromStream(inputStream: InputStream): String? {
        return runCatching {
            inputStream.bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    private fun extractJsonFromZip(inputStream: InputStream): String? {
        return runCatching {
            val result = StringBuilder()
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "backup.json" || entry.name.endsWith(".json")) {
                        result.append(zip.bufferedReader().readText())
                        break
                    }
                    entry = zip.nextEntry
                }
            }
            result.toString().ifEmpty { null }
        }.getOrNull()
    }

    suspend fun previewBackup(raw: String): BackupFile = json.decodeFromString(raw)

    private suspend fun writeSafetyBackup() {
        val dir = File(appContext.cacheDir, "backups").also { it.mkdirs() }
        val file = File(dir, "SafetyBackup_${LocalDate.now()}.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(createBackupJson().toByteArray())
            zip.closeEntry()
        }
    }

    private fun BudgetWithClientAndLines.toEditable(): EditableBudget =
        EditableBudget(
            id = budget.id,
            budgetNumber = budget.budgetNumber,
            issueDate = LocalDate.ofEpochDay(budget.issueDateEpochDay),
            expiryDate = LocalDate.ofEpochDay(budget.expiryDateEpochDay),
            projectTitle = budget.projectTitle,
            projectLocation = budget.projectLocation,
            status = budget.status,
            notes = budget.notes,
            pricesEnteredWithVat = budget.pricesEnteredWithVat,
            exemptFromVat = budget.exemptFromVat,
            client = EditableClient(client.name, client.vatNumber, client.address, client.phone, client.email, client.notes),
            lines = lines.sortedBy { it.position }.map {
                EditableLine(
                    id = it.id,
                    description = it.description,
                    quantity = it.quantity.toString(),
                    unit = it.unit,
                    unitPrice = (it.unitPriceExcludingVatCents / 100.0).toString(),
                    vatRate = it.vatRate.toString()
                )
            }.ifEmpty { listOf(EditableLine()) }
        )
}
