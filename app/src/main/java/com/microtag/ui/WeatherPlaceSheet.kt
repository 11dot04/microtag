package com.microtag.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.microtag.listener.WeatherWatcher
import com.microtag.weather.Place
import com.microtag.weather.Units
import com.microtag.weather.WeatherEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Where the weather is for. Replaces the old city sheet: it searches the real place list, offers "use my location",
 * and sets units. It keeps TagSettings' weatherAuto / weatherCity in step so the tile still reads them.
 */
@Composable
fun WeatherPlaceSheet(settings: TagSettings, onClose: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var units by remember { mutableStateOf(WeatherEngine.place(context).units) }

    fun finish() { WeatherWatcher.refresh(context); onClose() }
    fun useAuto() { WeatherEngine.setAuto(context); settings.weatherAuto = true; finish() }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        // Without permission Auto cannot find you, so keep the city that was set.
        if (ok) useAuto() else onClose()
    }

    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        delay(350)
        results = withContext(Dispatchers.IO) { runCatching { WeatherEngine.search(query.trim()) }.getOrDefault(emptyList()) }
        searching = false
    }

    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(TagColors.Black).padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Weather for", style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 26.sp, color = TagColors.White))
            Gap(12)
            TagButton("Use my location", {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) useAuto()
                else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }, Modifier.fillMaxWidth(), filled = settings.weatherAuto)
            Caption("Approximate location only, and only when the weather is fetched.")
            SectionLabel("Or a city")
            TagField(query, { query = it }, "Search")
            Gap(8)
            if (searching) Caption("Searching…")
            else if (query.trim().length >= 2 && results.isEmpty()) Caption("Nothing found. Check the spelling, or try a bigger town nearby.")
            results.forEach { p ->
                Plate(onClick = {
                    WeatherEngine.setCity(context, p); settings.weatherAuto = false; settings.weatherCity = p.label; finish()
                }, modifier = Modifier.padding(bottom = 4.dp)) {
                    Column {
                        Text(p.name, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TagColors.White))
                        Text(listOf(p.region, p.country).filter { it.isNotBlank() }.joinToString(", "), style = MonoStyle)
                    }
                }
            }
            SectionLabel("Units")
            Wrap {
                listOf(Units.AUTO to "Automatic", Units.CELSIUS to "°C", Units.FAHRENHEIT to "°F").forEach { (u, l) ->
                    TagChoice(l, units == u, { units = u; WeatherEngine.setUnits(context, u); WeatherWatcher.refresh(context) })
                }
            }
            Gap(14)
            TagButton("Done", onClose, Modifier.fillMaxWidth(), filled = false)
        }
    }
}
