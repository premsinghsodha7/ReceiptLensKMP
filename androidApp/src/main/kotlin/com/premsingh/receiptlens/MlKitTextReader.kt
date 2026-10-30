package com.premsingh.receiptlens

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.premsingh.receiptlens.OcrDocument
import com.premsingh.receiptlens.normalizeOcrDocument

/** The Android-only adapter translates ML Kit's native result into the common application model. */
class MlKitTextReader {
    private val lock = Any()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var activeRequests = 0
    private var closeRequested = false
    private var recognizerClosed = false

    fun recognize(image: InputImage, onResult: (Result<OcrDocument>) -> Unit) {
        synchronized(lock) {
            if (closeRequested || recognizerClosed) {
                onResult(Result.failure(IllegalStateException("The text reader is closed.")))
                return
            }
            activeRequests++
        }

        try {
            recognizer.process(image)
                .addOnSuccessListener { text ->
                    val lines = text.textBlocks.flatMap { block -> block.lines.map { it.text } }
                    onResult(Result.success(normalizeOcrDocument(text.text, lines)))
                }
                .addOnFailureListener { error -> onResult(Result.failure(error)) }
                .addOnCompleteListener { finishRequest() }
        } catch (error: Exception) {
            onResult(Result.failure(error))
            finishRequest()
        }
    }

    private fun finishRequest() {
        val closeNow = synchronized(lock) {
            activeRequests = (activeRequests - 1).coerceAtLeast(0)
            closeRequested && activeRequests == 0 && !recognizerClosed
        }
        if (closeNow) closeRecognizer()
    }

    fun close() {
        val closeNow = synchronized(lock) {
            closeRequested = true
            activeRequests == 0 && !recognizerClosed
        }
        if (closeNow) closeRecognizer()
    }

    private fun closeRecognizer() {
        val shouldClose = synchronized(lock) {
            if (recognizerClosed || activeRequests != 0) false
            else {
                recognizerClosed = true
                true
            }
        }
        if (shouldClose) recognizer.close()
    }
}
