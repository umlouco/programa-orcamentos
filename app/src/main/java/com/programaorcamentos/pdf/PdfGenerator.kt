package com.programaorcamentos.pdf

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.programaorcamentos.data.BudgetWithClientAndLines
import com.programaorcamentos.data.CompanyProfile
import com.programaorcamentos.data.formatMoney
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PdfGenerator(private val context: Context) {
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun generate(company: CompanyProfile?, full: BudgetWithClientAndLines): File {
        val pdfDir = File(context.cacheDir, "pdfs").also { it.mkdirs() }
        val safeClient = full.client.name.replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-').ifBlank { "Cliente" }
        val file = File(pdfDir, "${full.budget.budgetNumber}_$safeClient.pdf")
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
        val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; typeface = Typeface.DEFAULT_BOLD }
        var y = margin

        fun footer() {
            canvas.drawText("Página $pageNumber", pageWidth - margin - 55f, pageHeight - 24f, normal)
            company?.footerText?.takeIf { it.isNotBlank() }?.let { canvas.drawText(it.take(85), margin.toFloat(), pageHeight - 24f, normal) }
        }

        fun newPage() {
            footer()
            doc.finishPage(page)
            pageNumber += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = margin
        }

        fun line(text: String, paint: Paint = normal, indent: Int = 0) {
            if (y > pageHeight - 70) newPage()
            canvas.drawText(text, (margin + indent).toFloat(), y.toFloat(), paint)
            y += 16
        }

        val companyNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD }
        val col2X = pageWidth / 2
        var leftY = margin
        var rightY = margin

        company?.logoUri?.let { rawUri ->
            runCatching {
                context.contentResolver.openInputStream(android.net.Uri.parse(rawUri))?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()?.let { bitmap ->
                canvas.drawBitmap(bitmap, null, Rect(margin, margin, margin + 90, margin + 60), null)
                leftY = margin + 68
            }
        }

        if (company != null) {
            canvas.drawText(company.name.ifBlank { "Orçamento" }, margin.toFloat(), leftY.toFloat(), companyNamePaint)
            leftY += 18
            canvas.drawText(company.address, margin.toFloat(), leftY.toFloat(), normal)
            leftY += 16
            canvas.drawText("NIF: ${company.vatNumber}", margin.toFloat(), leftY.toFloat(), normal)
            leftY += 16
            canvas.drawText("Tel: ${company.phone}", margin.toFloat(), leftY.toFloat(), normal)
            leftY += 16
            canvas.drawText("Email: ${company.email}", margin.toFloat(), leftY.toFloat(), normal)
            leftY += 16
        } else {
            canvas.drawText("Orçamento", margin.toFloat(), leftY.toFloat(), title)
            leftY += 24
        }

        canvas.drawText("Orçamento ${full.budget.budgetNumber}", col2X.toFloat(), rightY.toFloat(), bold)
        rightY += 16
        canvas.drawText("Emissão: ${LocalDate.ofEpochDay(full.budget.issueDateEpochDay).format(dateFormat)}", col2X.toFloat(), rightY.toFloat(), normal)
        rightY += 16
        canvas.drawText("Validade: ${LocalDate.ofEpochDay(full.budget.expiryDateEpochDay).format(dateFormat)}", col2X.toFloat(), rightY.toFloat(), normal)
        rightY += 16
        if (full.budget.projectTitle.isNotBlank()) {
            canvas.drawText("Projeto: ${full.budget.projectTitle}", col2X.toFloat(), rightY.toFloat(), normal)
            rightY += 16
        }
        if (full.budget.projectLocation.isNotBlank()) {
            canvas.drawText("Local: ${full.budget.projectLocation}", col2X.toFloat(), rightY.toFloat(), normal)
            rightY += 16
        }
        rightY += 8
        canvas.drawText("Cliente", col2X.toFloat(), rightY.toFloat(), bold)
        rightY += 16
        canvas.drawText(full.client.name, col2X.toFloat(), rightY.toFloat(), bold)
        rightY += 16
        canvas.drawText(full.client.address, col2X.toFloat(), rightY.toFloat(), normal)
        rightY += 16
        if (full.client.vatNumber.isNotBlank()) {
            canvas.drawText("NIF: ${full.client.vatNumber}", col2X.toFloat(), rightY.toFloat(), normal)
            rightY += 16
        }
        if (full.client.phone.isNotBlank()) {
            canvas.drawText("Tel: ${full.client.phone}", col2X.toFloat(), rightY.toFloat(), normal)
            rightY += 16
        }

        y = maxOf(leftY, rightY) + 12
        canvas.drawLine(margin.toFloat(), y.toFloat(), (pageWidth - margin).toFloat(), y.toFloat(), normal)
        y += 10
        line("Descrição                                      Qtd   Un.  IVA       Total", bold)
        canvas.drawLine(margin.toFloat(), y.toFloat(), (pageWidth - margin).toFloat(), y.toFloat(), normal)
        y += 14
        full.lines.sortedBy { it.position }.forEach { item ->
            val description = item.description.chunked(46)
            line("${description.first().padEnd(46)} ${item.quantity} ${item.unit.take(4).padEnd(4)} ${item.vatRate}% ${formatMoney(item.totalIncludingVatCents, company?.currency ?: "EUR")}")
            description.drop(1).forEach { line(it, normal, 8) }
        }
        y += 10
        line("Subtotal: ${formatMoney(full.budget.subtotalExcludingVatCents, company?.currency ?: "EUR")}", bold)
        line("IVA: ${formatMoney(full.budget.vatTotalCents, company?.currency ?: "EUR")}", bold)
        line("Total: ${formatMoney(full.budget.totalIncludingVatCents, company?.currency ?: "EUR")}", title)
        if (full.budget.notes.isNotBlank()) {
            y += 10
            line("Notas:", bold)
            full.budget.notes.chunked(85).forEach { line(it) }
        }
        company?.paymentDetails?.takeIf { it.isNotBlank() }?.let {
            y += 8
            line("Pagamento:", bold)
            it.chunked(85).forEach { chunk -> line(chunk) }
        }
        footer()
        doc.finishPage(page)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun uriFor(file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
