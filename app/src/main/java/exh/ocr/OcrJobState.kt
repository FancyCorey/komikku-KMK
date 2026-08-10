package exh.ocr

import kotlinx.coroutines.flow.MutableStateFlow

// KMK --> OCR v0.1.0

object OcrJobState {
    val isRunning = MutableStateFlow(false)
    val activeProgress = MutableStateFlow<OcrIndexProgress?>(null)
}

// KMK <--
