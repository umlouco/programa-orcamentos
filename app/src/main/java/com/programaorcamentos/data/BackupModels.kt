package com.programaorcamentos.data

import kotlinx.serialization.Serializable

@Serializable
data class BackupFile(
    val format: String = "programa-orcamentos",
    val version: Int = 1,
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val nextBudgetNumber: Int,
    val company: CompanyProfile?,
    val budgets: List<BudgetBackup>
)

@Serializable
data class BudgetBackup(
    val budget: Budget,
    val client: Client,
    val lines: List<BudgetLine>
)
