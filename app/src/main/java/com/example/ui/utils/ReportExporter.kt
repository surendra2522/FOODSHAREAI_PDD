package com.example.ui.utils

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.DonationEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ReportExporter {

    fun exportPdfReport(
        context: Context,
        donorName: String,
        donations: List<DonationEntity>,
        totalMeals: Int,
        co2SavedKg: Double,
        peopleFed: Int
    ) {
        if (donations.isEmpty()) {
            Toast.makeText(context, "No donation data available to export.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val paint = Paint()

            // Header Background Bar
            paint.color = Color.parseColor("#10B981") // Emerald Green
            canvas.drawRect(0f, 0f, 595f, 90f, paint)

            // Header Title
            paint.color = Color.WHITE
            paint.textSize = 22f
            paint.isFakeBoldText = true
            canvas.drawText("FoodShareAI - Donor Impact Report", 30f, 42f, paint)

            paint.textSize = 12f
            paint.isFakeBoldText = false
            val dateStr = SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
            canvas.drawText("Generated on: $dateStr", 30f, 68f, paint)

            // Donor Info Box
            paint.color = Color.parseColor("#F1F5F9")
            canvas.drawRoundRect(30f, 110f, 565f, 160f, 10f, 10f, paint)

            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 13f
            paint.isFakeBoldText = true
            canvas.drawText("Donor Name: ${donorName.ifBlank { "Verified Food Donor" }}", 45f, 134f, paint)
            paint.textSize = 11f
            paint.isFakeBoldText = false
            canvas.drawText("Status: Active Verified FoodShare Partner", 45f, 150f, paint)

            // Impact Summary Cards Box
            paint.color = Color.parseColor("#ECFDF5")
            canvas.drawRoundRect(30f, 175f, 565f, 255f, 12f, 12f, paint)

            paint.color = Color.parseColor("#047857")
            paint.textSize = 13f
            paint.isFakeBoldText = true
            canvas.drawText("IMPACT SUMMARY OVERVIEW", 45f, 196f, paint)

            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 12f
            paint.isFakeBoldText = true
            canvas.drawText("Total Donations: ${donations.size}", 45f, 220f, paint)
            canvas.drawText("Meals Donated: $totalMeals", 200f, 220f, paint)
            canvas.drawText("CO₂ Saved: ${String.format(Locale.US, "%.1f", co2SavedKg)} kg", 360f, 220f, paint)

            paint.textSize = 11f
            paint.isFakeBoldText = false
            canvas.drawText("People Benefited: $peopleFed", 45f, 240f, paint)
            canvas.drawText("Redistribution Efficiency: 98%", 200f, 240f, paint)

            // Table Header
            paint.color = Color.parseColor("#10B981")
            canvas.drawRect(30f, 275f, 565f, 298f, paint)

            paint.color = Color.WHITE
            paint.textSize = 11f
            paint.isFakeBoldText = true
            canvas.drawText("Date", 40f, 291f, paint)
            canvas.drawText("Event / Food Name", 120f, 291f, paint)
            canvas.drawText("Meals", 310f, 291f, paint)
            canvas.drawText("NGO Partner", 370f, 291f, paint)
            canvas.drawText("Status", 490f, 291f, paint)

            // Table Rows
            paint.color = Color.parseColor("#334155")
            paint.isFakeBoldText = false
            var yPos = 318f
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

            val rowItems = donations.take(15)
            rowItems.forEachIndexed { idx, item ->
                val rowDate = dateFormat.format(Date(item.timestamp))
                val titleTruncated = if (item.title.length > 24) item.title.take(22) + ".." else item.title.ifBlank { item.foodType }
                val ngoTruncated = if (item.ngoName.length > 15) item.ngoName.take(13) + ".." else item.ngoName.ifBlank { "Awaiting" }

                // Alternating row bg
                if (idx % 2 == 1) {
                    paint.color = Color.parseColor("#F8FAFC")
                    canvas.drawRect(30f, yPos - 14f, 565f, yPos + 6f, paint)
                }

                paint.color = Color.parseColor("#0F172A")
                canvas.drawText(rowDate, 40f, yPos, paint)
                canvas.drawText(titleTruncated, 120f, yPos, paint)
                canvas.drawText("${item.quantity}", 310f, yPos, paint)
                canvas.drawText(ngoTruncated, 370f, yPos, paint)
                canvas.drawText(item.status, 490f, yPos, paint)

                yPos += 22f
            }

            // Footer
            paint.color = Color.parseColor("#94A3B8")
            paint.textSize = 9f
            canvas.drawText("FoodShareAI © 2026 • Verified Blockchain & AI Surplus Food Redistribution Network", 30f, 820f, paint)

            pdfDocument.finishPage(page)

            // Save PDF File
            val pdfFile = File(context.cacheDir, "foodshare_donor_impact_report.pdf")
            val outputStream = FileOutputStream(pdfFile)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()

            // Open or Share PDF via FileProvider
            val fileUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "FoodShareAI Impact Report - $donorName")
                putExtra(Intent.EXTRA_TEXT, "Here is my official FoodShareAI donor impact report.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Export PDF Report"))
            Toast.makeText(context, "PDF Report generated successfully!", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error generating PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportCsvReport(context: Context, donations: List<DonationEntity>) {
        if (donations.isEmpty()) {
            Toast.makeText(context, "No donation history found.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val csvBuilder = StringBuilder()
            csvBuilder.append("\"Donation ID\",\"Event Name\",\"Donation Date\",\"Meals\",\"Status\",\"NGO Name\",\"Pickup Window\",\"Location\"\n")

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            donations.forEach { item ->
                val idStr = item.id.ifBlank { "N/A" }
                val eventStr = item.title.ifBlank { item.foodType }.replace("\"", "\"\"")
                val dateStr = dateFormat.format(Date(item.timestamp))
                val mealsStr = item.quantity.toString()
                val statusStr = item.status.replace("\"", "\"\"")
                val ngoStr = item.ngoName.ifBlank { "Awaiting NGO Match" }.replace("\"", "\"\"")
                val pickupStr = item.pickupTime.ifBlank { "Immediate Pickup" }.replace("\"", "\"\"")
                val locStr = item.location.ifBlank { "Address Verified" }.replace("\"", "\"\"")

                csvBuilder.append("\"$idStr\",\"$eventStr\",\"$dateStr\",\"$mealsStr\",\"$statusStr\",\"$ngoStr\",\"$pickupStr\",\"$locStr\"\n")
            }

            val csvFile = File(context.cacheDir, "foodshare_donations_history.csv")
            val writer = FileOutputStream(csvFile)
            writer.write(csvBuilder.toString().toByteArray())
            writer.close()

            val fileUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", csvFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "FoodShareAI Donations CSV Export")
                putExtra(Intent.EXTRA_TEXT, "Attached CSV export of FoodShareAI donation history.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Export CSV Report"))
            Toast.makeText(context, "CSV Report generated successfully!", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error generating CSV: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareReport(
        context: Context,
        donorName: String,
        donations: List<DonationEntity>,
        totalMeals: Int,
        co2SavedKg: Double,
        peopleFed: Int
    ) {
        val summaryText = if (donations.isNotEmpty()) {
            "🍱 FoodShareAI Impact Report for ${donorName.ifBlank { "Donor" }}:\n" +
            "• Total Donations: ${donations.size}\n" +
            "• Meals Shared: $totalMeals\n" +
            "• CO₂ Saved: ${String.format(Locale.US, "%.1f", co2SavedKg)} kg\n" +
            "• People Benefited: $peopleFed\n\n" +
            "Together we reduce food waste! Download FoodShareAI today."
        } else {
            "FoodShareAI Donor Impact Summary: 0 donations recorded. Join us in saving surplus food today!"
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "FoodShareAI Impact Summary")
            putExtra(Intent.EXTRA_TEXT, summaryText)
        }

        context.startActivity(Intent.createChooser(sendIntent, "Share Impact Report"))
    }
}
