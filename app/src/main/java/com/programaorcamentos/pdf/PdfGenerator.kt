package com.programaorcamentos.pdf

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RectF
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
        
        // standard A4 dimensions
        val pageWidth = 595
        val pageHeight = 842
        val margin = 45 // Slightly increased margin for modern breathing room
        var pageNumber = 1
        
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        // Define a strict, cohesive professional color palette
        val primaryColor = Color.parseColor("#1A365D")   // Deep Corporate Blue
        val textColor = Color.parseColor("#2D3748")      // Charcoal (Never use pure #000000)
        val lightGray = Color.parseColor("#EDF2F7")      // Cool grey accent for table headers
        val dividerColor = Color.parseColor("#E2E8F0")   // Border lines
        
        // Define clean typographic weights
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = primaryColor }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = primaryColor }
        val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = textColor }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = textColor }
        val rightBoldPaint = Paint(boldPaint).apply { textAlign = Paint.Align.RIGHT }
        val rightNormalPaint = Paint(normalPaint).apply { textAlign = Paint.Align.RIGHT }
        
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = dividerColor }
        val headerBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = lightGray }

        var y = margin

        // Utility to fetch font baselines safely to ensure text never collides with boundaries
        fun getBaselineOffset(paint: Paint): Float {
            val metrics = paint.fontMetrics
            return -metrics.ascent
        }
        
        fun getRowHeight(paint: Paint): Float {
            val metrics = paint.fontMetrics
            return metrics.descent - metrics.ascent
        }

        fun footer() {
            canvas.drawLine(margin.toFloat(), pageHeight - 35f, (pageWidth - margin).toFloat(), pageHeight - 35f, linePaint)
            val footerY = pageHeight - 22f
            canvas.drawText("Página $pageNumber", (pageWidth - margin).toFloat(), footerY, rightNormalPaint)
            company?.footerText?.takeIf { it.isNotBlank() }?.let { 
                canvas.drawText(it.take(85), margin.toFloat(), footerY, normalPaint) 
            }
        }

        fun newPage() {
            footer()
            doc.finishPage(page)
            pageNumber += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = margin
        }

        fun line(text: String, paint: Paint = normalPaint, indent: Int = 0, alignment: Paint.Align = Paint.Align.LEFT) {
            if (y > pageHeight - 65) newPage()
            val x = if (alignment == Paint.Align.RIGHT) (pageWidth - margin).toFloat() else (margin + indent).toFloat()
            canvas.drawText(text, x, y + getBaselineOffset(paint), paint)
            y += (getRowHeight(paint) + 5).toInt()
        }

        // --- HEADER SECTION ---
        val col2X = pageWidth - margin
        var leftY = margin
        var rightY = margin

        // Logo Handler
        company?.logoUri?.let { rawUri ->
            runCatching {
                context.contentResolver.openInputStream(android.net.Uri.parse(rawUri))?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()?.let { bitmap ->
                val maxWidth = 110
                val maxHeight = 55
                val scale = minOf(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
                val drawWidth = (bitmap.width * scale).toInt()
                val drawHeight = (bitmap.height * scale).toInt()
                val scaled = Bitmap.createScaledBitmap(bitmap, drawWidth, drawHeight, true)
                canvas.drawBitmap(scaled, null, RectF(margin.toFloat(), margin.toFloat(), (margin + drawWidth).toFloat(), (margin + drawHeight).toFloat()), null)
                if (scaled !== bitmap) bitmap.recycle()
                leftY = margin + drawHeight + 15
            }
        }

        // Left Side: Company Profile
        if (company != null) {
            val compTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = primaryColor }
            canvas.drawText(company.name.ifBlank { "Orçamento" }, margin.toFloat(), leftY + getBaselineOffset(compTitlePaint), compTitlePaint)
            leftY += (getRowHeight(compTitlePaint) + 4).toInt()
            
            val details = listOf(company.address, "NIF: ${company.vatNumber}", "Tel: ${company.phone}", "Email: ${company.email}")
            details.forEach { text ->
                canvas.drawText(text, margin.toFloat(), leftY + getBaselineOffset(normalPaint), normalPaint)
                leftY += (getRowHeight(normalPaint) + 4).toInt()
            }
        } else {
            canvas.drawText("Orçamento", margin.toFloat(), leftY + getBaselineOffset(titlePaint), titlePaint)
            leftY += (getRowHeight(titlePaint) + 10).toInt()
        }

        // Right Side: Meta Details & Client Profile
        val budgetTitle = "Orçamento ${full.budget.budgetNumber}"
        canvas.drawText(budgetTitle, col2X.toFloat(), rightY + getBaselineOffset(titlePaint), rightBoldPaint)
        rightY += (getRowHeight(titlePaint) + 8).toInt()
        
        val metaData = mutableListOf(
            "Emissão: ${LocalDate.ofEpochDay(full.budget.issueDateEpochDay).format(dateFormat)}",
            "Validade: ${LocalDate.ofEpochDay(full.budget.expiryDateEpochDay).format(dateFormat)}"
        )
        if (full.budget.projectTitle.isNotBlank()) metaData.add("Projeto: ${full.budget.projectTitle}")
        if (full.budget.projectLocation.isNotBlank()) metaData.add("Local: ${full.budget.projectLocation}")
        
        metaData.forEach { text ->
            canvas.drawText(text, col2X.toFloat(), rightY + getBaselineOffset(normalPaint), rightNormalPaint)
            rightY += (getRowHeight(normalPaint) + 4).toInt()
        }
        
        // Client details block
        rightY += 12
        canvas.drawText("CLIENTE", col2X.toFloat(), rightY + getBaselineOffset(sectionPaint), rightBoldPaint)
        rightY += (getRowHeight(sectionPaint) + 4).toInt()
        canvas.drawText(full.client.name, col2X.toFloat(), rightY + getBaselineOffset(boldPaint), rightBoldPaint)
        rightY += (getRowHeight(boldPaint) + 4).toInt()
        canvas.drawText(full.client.address, col2X.toFloat(), rightY + getBaselineOffset(normalPaint), rightNormalPaint)
        rightY += (getRowHeight(normalPaint) + 4).toInt()
        
        if (full.client.vatNumber.isNotBlank()) {
            canvas.drawText("NIF: ${full.client.vatNumber}", col2X.toFloat(), rightY + getBaselineOffset(normalPaint), rightNormalPaint)
            rightY += (getRowHeight(normalPaint) + 4).toInt()
        }
        if (full.client.phone.isNotBlank()) {
            canvas.drawText("Tel: ${full.client.phone}", col2X.toFloat(), rightY + getBaselineOffset(normalPaint), rightNormalPaint)
            rightY += (getRowHeight(normalPaint) + 4).toInt()
        }

        y = maxOf(leftY, rightY) + 25

        // --- MODERN TABLE DESIGN ---
        // X Positions adjusted for clean optical flow (Right-aligning numeric data)
        val colDesc = margin + 6
        val colQtd = 340f
        val colUn = 390f
        val colIva = 445f
        val colTot = (pageWidth - margin - 6).toFloat()
        val tableLeft = margin.toFloat()
        val tableRight = (pageWidth - margin).toFloat()
        val tableRowHeight = 22f

        fun drawTableHeader() {
            val headerBottom = y + tableRowHeight
            // Draw clean background block for headers
            canvas.drawRect(tableLeft, y.toFloat(), tableRight, headerBottom, headerBackgroundPaint)
            canvas.drawLine(tableLeft, y.toFloat(), tableRight, y.toFloat(), linePaint)
            canvas.drawLine(tableLeft, headerBottom, tableRight, headerBottom, linePaint)
            
            val baseline = y + getBaselineOffset(boldPaint) + 4
            canvas.drawText("Descrição", colDesc.toFloat(), baseline, boldPaint)
            canvas.drawText("Qtd", colQtd, baseline, rightBoldPaint)
            canvas.drawText("Un.", colUn, baseline, rightBoldPaint)
            canvas.drawText("IVA", colIva, baseline, rightBoldPaint)
            canvas.drawText("Total", colTot, baseline, rightBoldPaint)
            y = headerBottom.toInt()
        }

        fun tableRow(desc: String, qtd: String, un: String, iva: String, tot: String) {
            val rowBottom = y + tableRowHeight
            val baseline = y + getBaselineOffset(normalPaint) + 5
            
            canvas.drawText(desc, colDesc.toFloat(), baseline, normalPaint)
            canvas.drawText(qtd, colQtd, baseline, rightNormalPaint)
            canvas.drawText(un, colUn, baseline, rightNormalPaint)
            canvas.drawText(iva, colIva, baseline, rightNormalPaint)
            canvas.drawText(tot, colTot, baseline, rightNormalPaint)
            
            canvas.drawLine(tableLeft, rowBottom, tableRight, rowBottom, linePaint)
            y = rowBottom.toInt()
        }

        drawTableHeader()

        full.lines.sortedBy { it.position }.forEach { item ->
            val descLines = item.description.chunked(42) // Safely break long lines
            if (y + tableRowHeight > pageHeight - 80) {
                newPage()
                drawTableHeader()
            }
            tableRow(
                desc = descLines.first(),
                qtd = item.quantity.toString(),
                un = item.unit.take(4),
                iva = "${item.vatRate}%",
                tot = formatMoney(item.totalIncludingVatCents, company?.currency ?: "EUR")
            )
            descLines.drop(1).forEach { continuation ->
                if (y + tableRowHeight > pageHeight - 80) {
                    newPage()
                    drawTableHeader()
                }
                tableRow(desc = continuation, qtd = "", un = "", iva = "", tot = "")
            }
        }

        // --- SUMMARY & TOTALS SECTION ---
        y += 15
        val summaryRightX = (pageWidth - margin).toFloat()
        val summaryLabelX = summaryRightX - 120f
        
        fun drawSummaryLine(label: String, value: String, paintLabel: Paint, paintValue: Paint) {
            if (y > pageHeight - 70) newPage()
            val baseline = y + getBaselineOffset(paintLabel)
            canvas.drawText(label, summaryLabelX, baseline, paintLabel)
            canvas.drawText(value, summaryRightX, baseline, paintValue)
            y += (getRowHeight(paintLabel) + 6).toInt()
        }

        val subtotalStr = formatMoney(full.budget.subtotalExcludingVatCents, company?.currency ?: "EUR")
        val vatStr = formatMoney(full.budget.vatTotalCents, company?.currency ?: "EUR")
        val totalStr = formatMoney(full.budget.totalIncludingVatCents, company?.currency ?: "EUR")

        drawSummaryLine("Subtotal:", subtotalStr, normalPaint, rightNormalPaint)
        drawSummaryLine("IVA:", vatStr, normalPaint, rightNormalPaint)
        
        // Emphasized Grand Total Box
        y += 4
        val totalBoxTop = y.toFloat()
        val totalBoxBottom = totalBoxTop + getRowHeight(titlePaint) + 10f
        val totalLabelPaint = Paint(titlePaint).apply { textSize = 14f; color = primaryColor }
        val totalValPaint = Paint(rightBoldPaint).apply { textSize = 14f; color = primaryColor }
        
        canvas.drawRect(summaryLabelX - 10f, totalBoxTop, summaryRightX, totalBoxBottom, headerBackgroundPaint)
        canvas.drawText("Total:", summaryLabelX, totalBoxTop + getBaselineOffset(totalLabelPaint) + 5f, totalLabelPaint)
        canvas.drawText(totalStr, summaryRightX, totalBoxTop + getBaselineOffset(totalValPaint) + 5f, totalValPaint)
        y = totalBoxBottom.toInt() + 20

        // --- ADDITIONAL INFORMATION METADATA ---
        if (full.budget.notes.isNotBlank()) {
            line("Notas:", sectionPaint)
            y += 2
            full.budget.notes.chunked(85).forEach { line(it, normalPaint) }
            y += 10
        }
        
        company?.paymentDetails?.takeIf { it.isNotBlank() }?.let { paymentDetails ->
            line("Dados de Pagamento:", sectionPaint)
            y += 2
            paymentDetails.chunked(85).forEach { chunk -> line(chunk, normalPaint) }
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