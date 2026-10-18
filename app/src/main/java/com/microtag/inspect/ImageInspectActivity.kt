package com.microtag.inspect

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.microtag.core.CapsuleCopyReceiver
import com.microtag.ui.BackBar
import com.microtag.ui.Montserrat
import com.microtag.ui.TagColors
import com.microtag.ui.TagMotion
import com.microtag.ui.TagSettings
import com.microtag.ui.TagTheme
import com.microtag.ui.Tick
import com.microtag.ui.tick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Screenshot Inspect. Share an image to Microtag and it reads the text on it (on the device, nothing is uploaded).
 * Things the detectors recognise are outlined: a UPI id, an amount, a date, an IFSC, a link, a portion of food.
 * Tap an outline for its card. Tap any other word to inspect that word; press and hold to take the whole line.
 * QR codes and product barcodes are found too.
 */
class ImageInspectActivity : ComponentActivity() {

    private class Scan(val bitmap: Bitmap, val lines: List<OcrLine>, val findings: List<Finding>)

    /** What was tapped: the text, where it is on the image, and its card once resolved (null while resolving or when there is none). */
    private class Picked(val text: String, val box: IBox?, val result: InspectResult?, val resolving: Boolean)

    private var scan by mutableStateOf<Scan?>(null)
    private var failed by mutableStateOf("")
    private var picked by mutableStateOf<Picked?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        // This activity can start without MainActivity ever running, so it applies the look-and-feel itself.
        val settings = TagSettings(applicationContext)
        TagColors.useMaterialYou(applicationContext, settings.materialYou)
        TagMotion.enabled = !settings.reducedMotion
        TagMotion.haptics = settings.haptics

        val uri = imageUri(intent)
        if (uri == null) failed = "There's no image to read." else lifecycleScope.launch { read(uri) }

        setContent { TagTheme { Screen() } }
    }

    @Suppress("DEPRECATION")
    private fun imageUri(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

    // ------------------------------------------------------ reading the image

    private suspend fun read(uri: Uri) {
        val result = withContext(Dispatchers.IO) {
            try {
                val bmp = decode(uri) ?: return@withContext null
                val input = InputImage.fromBitmap(bmp, 0)
                val text = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(input).await()
                val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                    val words = line.elements.mapNotNull { e ->
                        e.boundingBox?.let { OcrWord(e.text, IBox(it.left, it.top, it.right, it.bottom)) }
                    }
                    if (words.isEmpty()) null else OcrLine(words)
                }
                val findings = ArrayList<Finding>(ScreenshotScan.analyze(lines, bmp.height, InspectEngine.envFor(applicationContext)))
                // QR codes and product barcodes, kept apart from the text.
                try {
                    val opts = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                        .build()
                    BarcodeScanning.getClient(opts).process(input).await().forEach { b ->
                        val raw = b.rawValue ?: return@forEach
                        val box = b.boundingBox ?: return@forEach
                        findings.add(Finding(raw, IBox(box.left, box.top, box.right, box.bottom), "barcode", 1.0))
                    }
                } catch (_: Throwable) {
                    // Text is still worth showing without them.
                }
                Scan(bmp, lines, findings)
            } catch (t: Throwable) {
                null
            }
        }
        if (result == null) failed = "Couldn't read that image." else scan = result
    }

    private fun decode(uri: Uri): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val side = maxOf(info.size.width, info.size.height)
            if (side > 3000) decoder.setTargetSampleSize(side / 3000 + 1)
        }
    } catch (_: Throwable) {
        null
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }

    // ------------------------------------------------------ picking

    private fun pick(text: String, box: IBox?) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        picked = Picked(clean, box, null, true)
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { InspectEngine.resolveBlocking(applicationContext, clean) }
            // Only if the user hasn't tapped something else meanwhile.
            if (picked?.text == clean) picked = Picked(clean, box, r, false)
        }
    }

    private fun share(p: InspectPayload) {
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, p.shareText())
            }
            startActivity(Intent.createChooser(send, null))
        } catch (_: Exception) {
        }
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------ screen

    @Composable
    private fun Screen() {
        val view = LocalView.current
        var flash by remember { mutableStateOf("") }
        LaunchedEffect(flash) {
            if (flash.isNotEmpty()) {
                delay(900)
                flash = ""
            }
        }
        val onCopy: (String) -> Unit = { t ->
            if (t.isNotBlank()) {
                CapsuleCopyReceiver.copy(this@ImageInspectActivity, t)
                view.tick(Tick.Confirm)
                flash = t
            }
        }
        Box(Modifier.fillMaxSize().background(TagColors.Black)) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                BackBar(onBack = { finish() })
                val s = scan
                when {
                    failed.isNotEmpty() -> Message(failed)
                    s == null -> Message("Reading the screenshot…")
                    else -> Reader(s)
                }
            }

            val p = picked
            if (p != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { picked = null },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(Modifier.navigationBarsPadding().padding(horizontal = 14.dp).padding(bottom = 12.dp)) {
                        val r = p.result
                        when {
                            p.resolving -> Message("Looking it up…")
                            r != null -> InspectCard(
                                p = r.toPayload(), flash = flash, onCopy = onCopy,
                                onShare = { share(r.toPayload()) }, onOpen = { openLink(r.openUrl) },
                                onPin = { action -> if (action == "pin") PinEngine.pin(applicationContext, r.copyText.ifBlank { r.title }) }
                            )
                            else -> NoResult(p.text, onCopy) {
                                PinEngine.pin(applicationContext, p.text)
                                Toast.makeText(this@ImageInspectActivity, "Pinned", Toast.LENGTH_SHORT).show()
                                picked = null
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Message(text: String) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = TagColors.Outline)
            )
        }
    }

    @Composable
    private fun Reader(s: Scan) {
        var shown by remember { mutableStateOf(IntSize.Zero) }
        val img = remember(s.bitmap) { s.bitmap.asImageBitmap() }
        val current = picked
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                if (s.findings.isEmpty()) "Nothing recognised on its own. Tap any word to inspect it."
                else "${s.findings.size} found. Tap an outline, or tap any word. Press and hold takes the whole line.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, color = TagColors.Outline)
            )
            Spacer(Modifier.height(6.dp))
            Image(
                img, contentDescription = null, contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { shown = it }
                    .pointerInput(s, shown) {
                        val scale = if (shown.width > 0) s.bitmap.width.toFloat() / shown.width else 1f
                        val pad = 6.dp.toPx() * scale
                        detectTapGestures(
                            onTap = { at ->
                                val x = at.x * scale
                                val y = at.y * scale
                                val f = ScreenshotScan.findingAt(s.findings, x, y, pad)
                                if (f != null) pick(f.text, f.box)
                                else ScreenshotScan.wordAt(s.lines, x, y, pad)?.let { pick(it.text, it.box) }
                            },
                            onLongPress = { at ->
                                ScreenshotScan.lineAt(s.lines, at.x * scale, at.y * scale, pad)?.let { pick(it.text, it.box) }
                            }
                        )
                    }
                    .drawWithContent {
                        drawContent()
                        val k = if (s.bitmap.width > 0) size.width / s.bitmap.width else 1f
                        val corner = CornerRadius(6.dp.toPx())
                        val inset = 2.dp.toPx()
                        for (f in s.findings) {
                            drawRoundRect(
                                TagColors.Lime, Offset(f.box.l * k - inset, f.box.t * k - inset),
                                Size((f.box.r - f.box.l) * k + inset * 2, (f.box.b - f.box.t) * k + inset * 2),
                                corner, style = Stroke(2.dp.toPx())
                            )
                        }
                        current?.box?.let { b ->
                            drawRoundRect(
                                TagColors.Lime.copy(alpha = 0.28f), Offset(b.l * k - inset, b.t * k - inset),
                                Size((b.r - b.l) * k + inset * 2, (b.b - b.t) * k + inset * 2), corner
                            )
                        }
                    }
            )
            if (s.findings.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    s.findings.forEach { f ->
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .border(1.5.dp, TagColors.Outline, CircleShape)
                                .clickable { pick(f.text, f.box) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                f.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TagColors.White)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    /** A word or line with no card: copy it, or pin it. */
    @Composable
    private fun NoResult(text: String, onCopy: (String) -> Unit, onPin: () -> Unit) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(TagColors.Surface).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text, maxLines = 4, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp, color = TagColors.White)
            )
            Text(
                "Nothing to look up for this.",
                style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = TagColors.Outline)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipButton("PIN", filled = true, onClick = onPin)
                ChipButton("COPY", filled = false) { onCopy(text) }
            }
        }
    }

    @Composable
    private fun ChipButton(text: String, filled: Boolean, onClick: () -> Unit) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(if (filled) TagColors.Lime else TagColors.Black)
                .border(1.5.dp, if (filled) TagColors.Lime else TagColors.Outline, CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text,
                style = TextStyle(
                    fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp,
                    color = if (filled) TagColors.OnLime else TagColors.White
                )
            )
        }
    }
}
