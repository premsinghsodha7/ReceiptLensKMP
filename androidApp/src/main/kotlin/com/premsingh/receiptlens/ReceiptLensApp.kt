package com.premsingh.receiptlens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class LensPalette(
    val name: String,
    val background: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val primary: Color,
    val accent: Color,
    val line: Color,
    val dark: Boolean,
)

private val palettes = listOf(
    LensPalette("Sage", Color(0xFFF3F5F0), Color.White, Color(0xFF182A2A), Color(0xFF647572), Color(0xFF17685D), Color(0xFFE6B76B), Color(0xFFE2E8E1), false),
    LensPalette("Midnight", Color(0xFF101A20), Color(0xFF1C2A31), Color(0xFFE7F0EC), Color(0xFFA3B6B0), Color(0xFF91D6BC), Color(0xFFF1BE71), Color(0xFF34444A), true),
    LensPalette("Clay", Color(0xFFF8F1EB), Color.White, Color(0xFF352821), Color(0xFF7B6C62), Color(0xFF98563B), Color(0xFFE6AA70), Color(0xFFEDE0D7), false),
)

@Composable
fun ReceiptLensApp(viewModel: ReceiptLensViewModel, onChoosePhoto: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var paletteIndex by remember { mutableStateOf(0) }
    val palette = palettes[paletteIndex]
    val scheme = if (palette.dark) {
        darkColorScheme(primary = palette.primary, secondary = palette.accent, background = palette.background, surface = palette.surface, onSurface = palette.ink, onBackground = palette.ink)
    } else {
        lightColorScheme(primary = palette.primary, secondary = palette.accent, background = palette.background, surface = palette.surface, onSurface = palette.ink, onBackground = palette.ink)
    }

    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = palette.background) {
            Column(Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        BrandHeader(palette, state.saved.size, onThemeClick = { paletteIndex = (paletteIndex + 1) % palettes.size })
                    }
                    if (state.page == LensPage.Scan) {
                        item {
                            ScanHero(
                                palette = palette,
                                draftCount = state.reviews.size,
                                onChoosePhoto = onChoosePhoto,
                                onSample = viewModel::scanSample,
                            )
                        }
                        if (state.phase == ScanPhase.Preparing || state.phase == ScanPhase.Recognizing) {
                            item { StatusCard(state.note ?: "Working…", "Your current review drafts stay available while this image is processed.", palette, loading = true) }
                        }
                        state.error?.let { error -> item { StatusCard("That scan needs another try", error, palette, loading = false) } }
                        state.message?.let { message -> item { StatusCard("Review update", message, palette, loading = false, positive = true) } }
                        if (state.reviews.isNotEmpty()) {
                            item { ReviewDraftPicker(state.reviews, state.selectedReviewId, palette, viewModel::selectReview) }
                            val review = state.reviews.firstOrNull { it.id == state.selectedReviewId }
                            if (review != null) {
                                item { SourceReceiptCard(review.image, review.document.lines.size, palette) }
                                item {
                                    EditableFieldsCard(
                                        fields = review.fields,
                                        palette = palette,
                                        onChange = viewModel::updateField,
                                    )
                                }
                                item {
                                    ReviewActions(
                                        palette = palette,
                                        onSave = viewModel::saveCurrent,
                                        onDiscard = { viewModel.discardReview(review.id) },
                                    )
                                }
                                item { RecognizedTextCard(review.document.fullText, palette) }
                            }
                        } else {
                            item { EmptyReviewCard(palette) }
                        }
                        item { PrivacyNote(palette) }
                    } else {
                        item { SavedHeader(state.saved.size, palette) }
                        state.message?.let { message ->
                            item { StatusCard("Saved", message, palette, loading = false, positive = true) }
                        }
                        if (state.saved.isEmpty()) {
                            item { EmptySavedCard(palette) }
                        } else {
                            items(state.saved, key = { it.id }) { saved ->
                                SavedReceiptCard(saved, palette, onDelete = { viewModel.deleteSaved(saved.id) })
                            }
                        }
                        item { PrivacyNote(palette) }
                    }
                }
                NavigationBar(containerColor = palette.surface) {
                    NavigationBarItem(
                        selected = state.page == LensPage.Scan,
                        onClick = { viewModel.showPage(LensPage.Scan) },
                        icon = { Text("⌕", fontSize = 21.sp, fontWeight = FontWeight.Bold) },
                        label = { Text("Scan") },
                    )
                    NavigationBarItem(
                        selected = state.page == LensPage.Saved,
                        onClick = { viewModel.showPage(LensPage.Saved) },
                        icon = { Text("▤", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                        label = { Text("Saved ${state.saved.size}") },
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandHeader(palette: LensPalette, savedCount: Int, onThemeClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = palette.primary, shape = RoundedCornerShape(16.dp), modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("R", color = if (palette.dark) Color(0xFF10201F) else Color.White, fontWeight = FontWeight.Black, fontSize = 25.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Receipt Lens", color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("PRIVATE BY DESIGN · $savedCount SAVED", color = palette.muted, fontSize = 9.sp, letterSpacing = 1.1.sp)
        }
        OutlinedButton(onClick = onThemeClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            Text(palette.name, color = palette.ink, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ScanHero(palette: LensPalette, draftCount: Int, onChoosePhoto: () -> Unit, onSample: () -> Unit) {
    Surface(color = if (palette.dark) Color(0xFF263B3D) else Color(0xFF18383A), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0x337FC4A8), shape = CircleShape) {
                    Text("ON-DEVICE OCR", Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = Color(0xFFB8E9D2), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("${draftCount} ${if (draftCount == 1) "draft" else "drafts"}", color = Color(0xFFCAE0D8), fontSize = 11.sp)
            }
            Spacer(Modifier.height(18.dp))
            Text("From pixels\nto a clearer record.", color = Color.White, fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Extract a few receipt fields, then check every suggestion against the original.", color = Color(0xFFD0DFD8), fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onChoosePhoto,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB9E7D0), contentColor = Color(0xFF163638)),
            ) { Text("Choose a receipt photo", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSample,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x668FC4AE)),
            ) { Text("Try the sample receipt", color = Color.White, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(10.dp))
            Text("No camera permission · No upload · Latin script demo", color = Color(0xFFB6CAC3), fontSize = 10.sp)
        }
    }
}

@Composable
private fun ReviewDraftPicker(reviews: List<ReceiptReview>, selectedId: Long?, palette: LensPalette, onSelect: (Long) -> Unit) {
    Column {
        SectionTitle("Review drafts", "Keep a few scans in progress", palette)
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            reviews.forEachIndexed { index, review ->
                val selected = review.id == selectedId
                OutlinedButton(
                    onClick = { onSelect(review.id) },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) palette.primary else palette.line),
                ) {
                    Text(review.fields.merchant.ifBlank { "Receipt ${reviews.size - index}" }.take(18), color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun SourceReceiptCard(image: android.graphics.Bitmap, lineCount: Int, palette: LensPalette) {
    Card(colors = CardDefaults.cardColors(containerColor = palette.surface), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SOURCE IMAGE", color = palette.muted, fontSize = 9.sp, letterSpacing = 1.15.sp, fontWeight = FontWeight.Bold)
                    Text("$lineCount lines recognized", color = palette.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("ORIGINAL", color = palette.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "Selected receipt image to compare with the extracted fields",
                modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)).background(palette.background),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun EditableFieldsCard(fields: EditableReceipt, palette: LensPalette, onChange: (ReceiptField, String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = palette.surface), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Check the details", "Suggestions are editable, never verified", palette)
            ReceiptField.entries.forEach { field ->
                val (label, value) = when (field) {
                    ReceiptField.Merchant -> "Merchant" to fields.merchant
                    ReceiptField.InvoiceNumber -> "Invoice number" to fields.invoiceNumber
                    ReceiptField.Date -> "Date as printed" to fields.date
                    ReceiptField.Total -> "Total as printed" to fields.total
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { onChange(field, it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(label) },
                    singleLine = true,
                    shape = RoundedCornerShape(15.dp),
                )
            }
            Text("Check digits, currency, and date format against the image. OCR can confuse similar characters.", color = palette.muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun ReviewActions(palette: LensPalette, onSave: () -> Unit, onDiscard: () -> Unit) {
    Column {
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Confirm & save to session", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onDiscard, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Discard this draft", color = palette.muted)
        }
    }
}

@Composable
private fun RecognizedTextCard(text: String, palette: LensPalette) {
    var expanded by remember(text) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = palette.surface), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("FULL RECOGNIZED TEXT", color = palette.muted, fontSize = 9.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
                    Text("Compare the extraction with every line", color = palette.ink, fontSize = 13.sp)
                }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Show") }
            }
            if (expanded) {
                HorizontalDivider(color = palette.line)
                Spacer(Modifier.height(10.dp))
                Text(text.ifBlank { "No text found." }, color = palette.ink, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun SavedHeader(count: Int, palette: LensPalette) {
    Column {
        Text("Your review shelf", color = palette.ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("$count confirmed ${if (count == 1) "receipt" else "receipts"} · held in this session only", color = palette.muted, fontSize = 12.sp)
    }
}

@Composable
private fun SavedReceiptCard(receipt: SavedReceipt, palette: LensPalette, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = palette.surface), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = palette.background, shape = RoundedCornerShape(13.dp), modifier = Modifier.size(42.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("✓", color = palette.primary, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(receipt.fields.merchant.ifBlank { "Untitled receipt" }, color = palette.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(receipt.fields.date.ifBlank { "Date not confirmed" }, color = palette.muted, fontSize = 11.sp)
                }
                Text(receipt.fields.total.ifBlank { "No total" }, color = palette.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(13.dp))
            HorizontalDivider(color = palette.line)
            Spacer(Modifier.height(10.dp))
            Text("Invoice  ${receipt.fields.invoiceNumber.ifBlank { "Not found" }}", color = palette.muted, fontSize = 12.sp)
            TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) { Text("Remove", color = palette.muted) }
        }
    }
}

@Composable
private fun EmptyReviewCard(palette: LensPalette) {
    Card(colors = CardDefaults.cardColors(containerColor = palette.surface), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("▧", color = palette.primary, fontSize = 32.sp)
            Spacer(Modifier.height(6.dp))
            Text("A careful review starts here", color = palette.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Choose a photo or try the sample. You can edit every suggestion and keep multiple drafts open.", color = palette.muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun EmptySavedCard(palette: LensPalette) {
    Card(colors = CardDefaults.cardColors(containerColor = palette.surface), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Nothing saved yet", color = palette.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Confirm a reviewed receipt to add it to this temporary session shelf.", color = palette.muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun StatusCard(title: String, message: String, palette: LensPalette, loading: Boolean, positive: Boolean = false) {
    val tint = when {
        positive -> palette.primary
        loading -> palette.primary
        else -> Color(0xFFB95B3F)
    }
    Card(colors = CardDefaults.cardColors(containerColor = palette.surface), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.Top) {
            Surface(color = tint.copy(alpha = 0.12f), shape = CircleShape, modifier = Modifier.size(30.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(if (loading) "…" else if (positive) "✓" else "!", color = tint, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(message, color = palette.muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun PrivacyNote(palette: LensPalette) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.Top) {
        Text("◈", color = palette.primary, fontSize = 15.sp)
        Spacer(Modifier.width(8.dp))
        Text("No account or backend. Draft images and OCR text stay in memory; the session shelf keeps fields only. Closing the app clears them.", color = palette.muted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, palette: LensPalette) {
    Column {
        Text(title, color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(subtitle, color = palette.muted, fontSize = 11.sp)
    }
}
