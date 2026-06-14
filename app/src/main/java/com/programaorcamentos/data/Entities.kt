package com.programaorcamentos.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class BudgetStatus(val displayName: String) {
    Draft("Rascunho"),
    Sent("Enviado"),
    Accepted("Aceite"),
    Rejected("Rejeitado"),
    Cancelled("Cancelado")
}

@Serializable
@Entity
data class CompanyProfile(
    @PrimaryKey val id: Long = 1,
    val name: String = "",
    val address: String = "",
    val vatNumber: String = "",
    val phone: String = "",
    val email: String = "",
    val additionalContacts: String = "",
    val logoUri: String? = null,
    val defaultVatRate: Double = 23.0,
    val currency: String = "EUR",
    val defaultValidityDays: Int = 30,
    val paymentDetails: String = "",
    val footerText: String = ""
)

@Serializable
@Entity
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val vatNumber: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val notes: String = ""
)

@Serializable
@Entity(indices = [Index(value = ["budgetNumber"], unique = true), Index("clientId")])
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val budgetNumber: String,
    val clientId: Long,
    val issueDateEpochDay: Long,
    val expiryDateEpochDay: Long,
    val projectTitle: String = "",
    val projectLocation: String = "",
    val status: BudgetStatus = BudgetStatus.Draft,
    val notes: String = "",
    val pricesEnteredWithVat: Boolean = false,
    val exemptFromVat: Boolean = false,
    val subtotalExcludingVatCents: Long = 0,
    val vatTotalCents: Long = 0,
    val totalIncludingVatCents: Long = 0,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Budget::class,
            parentColumns = ["id"],
            childColumns = ["budgetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("budgetId")]
)
data class BudgetLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val budgetId: Long,
    val position: Int,
    val description: String,
    val quantity: Double,
    val unit: String,
    val unitPriceExcludingVatCents: Long,
    val vatRate: Double,
    val subtotalExcludingVatCents: Long,
    val vatAmountCents: Long,
    val totalIncludingVatCents: Long
)
