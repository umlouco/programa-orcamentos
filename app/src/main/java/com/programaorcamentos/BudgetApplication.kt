package com.programaorcamentos

import android.app.Application
import com.programaorcamentos.data.AppDatabase
import com.programaorcamentos.data.BudgetRepository
import com.programaorcamentos.pdf.PdfGenerator

class BudgetApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { BudgetRepository(this, database) }
    val pdfGenerator by lazy { PdfGenerator(this) }
}
