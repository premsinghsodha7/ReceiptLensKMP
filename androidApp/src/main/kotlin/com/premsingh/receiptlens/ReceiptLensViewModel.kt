package com.premsingh.receiptlens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.premsingh.receiptlens.OcrDocument
import com.premsingh.receiptlens.ReceiptCandidates
import com.premsingh.receiptlens.extractReceiptCandidates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LensPage { Scan, Saved }
enum class ScanPhase { Idle, Preparing, Recognizing, NoText, Failed, Ready }

data class EditableReceipt(
    val merchant: String = "",
    val invoiceNumber: String = "",
    val date: String = "",
    val total: String = "",
)

data class ReceiptReview(
    val id: Long,
    val image: Bitmap,
    val document: OcrDocument,
    val fields: EditableReceipt,
)

data class SavedReceipt(
    val id: Long,
    val fields: EditableReceipt,
)

data class ReceiptLensState(
    val page: LensPage = LensPage.Scan,
    val phase: ScanPhase = ScanPhase.Idle,
    val note: String? = null,
    val error: String? = null,
    val reviews: List<ReceiptReview> = emptyList(),
    val selectedReviewId: Long? = null,
    val saved: List<SavedReceipt> = emptyList(),
    val message: String? = null,
)

class ReceiptLensViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val reader = MlKitTextReader()
    private val mutableState = MutableStateFlow(ReceiptLensState())
    val state: StateFlow<ReceiptLensState> = mutableState.asStateFlow()
    private var nextId = 1L
    private var requestGeneration = 0L

    fun showPage(page: LensPage) {
        mutableState.update { it.copy(page = page, message = null) }
    }

    fun scanSample() {
        if (!canStartAnotherScan()) return
        val image = createSampleReceipt()
        val input = InputImage.fromBitmap(image, 0)
        startRecognition(input, image)
    }

    fun scanPhoto(uri: Uri) {
        if (!canStartAnotherScan()) return
        val generation = beginRequest()
        viewModelScope.launch {
            try {
                val (inputImage, preview) = withContext(Dispatchers.IO) {
                    InputImage.fromFilePath(appContext, uri) to decodePreview(uri)
                }
                if (generation != requestGeneration) return@launch
                mutableState.update {
                    it.copy(phase = ScanPhase.Recognizing, note = "Reading text on this device…", error = null)
                }
                recognize(inputImage, preview, generation)
            } catch (error: Exception) {
                if (generation == requestGeneration) {
                    mutableState.update {
                        it.copy(phase = ScanPhase.Failed, note = null, error = "Could not open that image. Choose another photo and try again.")
                    }
                }
            }
        }
    }

    private fun startRecognition(input: InputImage, preview: Bitmap) {
        val generation = beginRequest()
        mutableState.update { it.copy(phase = ScanPhase.Recognizing, note = "Reading the sample on this device…", error = null) }
        recognize(input, preview, generation)
    }

    private fun canStartAnotherScan(): Boolean {
        if (mutableState.value.reviews.size < MAX_OPEN_REVIEWS) return true
        mutableState.update { it.copy(message = "You have five open review drafts. Save or discard one before scanning another.") }
        return false
    }

    private fun beginRequest(): Long {
        val generation = ++requestGeneration
        mutableState.update { it.copy(phase = ScanPhase.Preparing, note = "Preparing image…", error = null, message = null) }
        return generation
    }

    private fun recognize(input: InputImage, preview: Bitmap, generation: Long) {
        reader.recognize(input) { result ->
            if (generation != requestGeneration) return@recognize
            result.fold(
                onSuccess = { document ->
                    if (document.lines.isEmpty()) {
                        mutableState.update {
                            it.copy(phase = ScanPhase.NoText, note = null, error = "No readable text found. Try a sharper, brighter photo with the receipt filling more of the frame.")
                        }
                    } else {
                        val suggested = extractReceiptCandidates(document.lines).toEditableReceipt()
                        val review = ReceiptReview(nextId++, preview, document, suggested)
                        mutableState.update {
                            it.copy(
                                phase = ScanPhase.Ready,
                                note = null,
                                error = null,
                                reviews = it.reviews + review,
                                selectedReviewId = review.id,
                                message = "Text recognized. Check every field against the image before saving.",
                            )
                        }
                    }
                },
                onFailure = {
                    mutableState.update {
                        it.copy(phase = ScanPhase.Failed, note = null, error = "Recognition failed. Your existing review drafts are still here. Try another image.")
                    }
                },
            )
        }
    }

    private fun decodePreview(uri: Uri): Bitmap {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("Image is no longer available")
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 1200 || bounds.outHeight / sampleSize > 1200) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Could not create a preview for this image")
    }

    fun selectReview(id: Long) {
        mutableState.update { it.copy(selectedReviewId = id, message = null) }
    }

    fun discardReview(id: Long) {
        mutableState.update { current ->
            val remaining = current.reviews.filterNot { it.id == id }
            current.copy(
                reviews = remaining,
                selectedReviewId = if (current.selectedReviewId == id) remaining.lastOrNull()?.id else current.selectedReviewId,
                message = "Draft removed from this session.",
            )
        }
    }

    fun updateField(field: ReceiptField, value: String) {
        mutableState.update { current ->
            current.copy(reviews = current.reviews.map { review ->
                if (review.id != current.selectedReviewId) review else review.copy(fields = review.fields.with(field, value))
            })
        }
    }

    fun saveCurrent() {
        mutableState.update { current ->
            val review = current.reviews.firstOrNull { it.id == current.selectedReviewId } ?: return@update current
            if (review.document.lines.isEmpty()) return@update current
            val remaining = current.reviews.filterNot { it.id == review.id }
            val saved = SavedReceipt(nextId++, review.fields)
            current.copy(
                saved = listOf(saved) + current.saved,
                reviews = remaining,
                selectedReviewId = remaining.lastOrNull()?.id,
                message = "Saved to this session. Nothing was sent or written to device storage.",
                page = LensPage.Saved,
            )
        }
    }

    fun deleteSaved(id: Long) {
        mutableState.update { it.copy(saved = it.saved.filterNot { saved -> saved.id == id }, message = "Saved item removed.") }
    }

    override fun onCleared() {
        requestGeneration++
        reader.close()
        super.onCleared()
    }

    private companion object {
        const val MAX_OPEN_REVIEWS = 5
    }
}

enum class ReceiptField { Merchant, InvoiceNumber, Date, Total }

private fun ReceiptCandidates.toEditableReceipt() = EditableReceipt(
    merchant = merchant.orEmpty(),
    invoiceNumber = invoiceNumber.orEmpty(),
    date = date.orEmpty(),
    total = total.orEmpty(),
)

private fun EditableReceipt.with(field: ReceiptField, value: String) = when (field) {
    ReceiptField.Merchant -> copy(merchant = value)
    ReceiptField.InvoiceNumber -> copy(invoiceNumber = value)
    ReceiptField.Date -> copy(date = value)
    ReceiptField.Total -> copy(total = value)
}
