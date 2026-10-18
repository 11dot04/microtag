package com.microtag.inspect

import org.json.JSONObject
import java.util.Locale

// ==========================================================
// FOOD: what is in a portion. "2 rotis", "100 g paneer", "1 cup rice", a bare "banana", or a barcode.
//   - a quantity and a food is a clear request (0.88). A bare food name ranks below a dictionary word (0.36), so
//     "banana" still defines; say "1 banana" to get the numbers. Clipboard needs the quantity.
//   - a barcode is looked up on Open Food Facts (online).
// ==========================================================

internal object FoodDetector {

    fun candidates(text: String, source: Source): List<Candidate> {
        val t = text.trim()
        if (FoodTable.validGtin(t.replace(" ", ""))) {
            val code = t.replace(" ", "")
            return listOf(Candidate("barcode", 0.82, slow = true) { lookupBarcode(code) })
        }
        val q = FoodTable.parse(t) ?: return emptyList()
        if (source == Source.CLIPBOARD && !q.explicit) return emptyList()
        return listOf(Candidate("food", if (q.explicit) 0.88 else 0.36) { build(q) })
    }

    private fun num(v: Double): String = when {
        v >= 100 -> Math.round(v).toString()
        v >= 10 -> Fmt.trimmed(v, 0)
        else -> Fmt.trimmed(v, 1)
    }

    private fun qtyText(q: FoodQuery): String {
        val n = Fmt.trimmed(q.qty, 2)
        val unit = when (q.unit) {
            null -> "×"
            "katori" -> if (q.qty == 1.0) "katori" else "katoris"
            else -> q.unit
        }
        return if (q.unit == null) "$n × ${q.food.name}" else "$n $unit ${q.food.name}"
    }

    internal fun build(q: FoodQuery): InspectResult {
        val grams = FoodTable.grams(q)
        val p = FoodTable.portion(q.food, grams)
        val kcal = Math.round(p.kcal).toInt()
        val estimated = (q.unit == "cup" && q.food.cupG <= 0.0) || q.unit == "bowl" || q.unit == "plate"
        val rows = listOf(
            "Protein" to "${num(p.protein)} g",
            "Carbs" to "${num(p.carbs)} g",
            "Fat" to "${num(p.fat)} g",
            "Fibre" to "${num(p.fibre)} g",
            "Per 100 g" to "${Math.round(q.food.kcal)} kcal"
        )
        return InspectResult(
            domain = "FOOD", pill = "$kcal kcal", title = q.food.name.replaceFirstChar { it.titlecase(Locale.ROOT) },
            cardTitle = "$kcal kcal", cardText = "${qtyText(q)} · ${num(grams)} g",
            subtitle = "${qtyText(q)} · ${num(grams)} g",
            rows = rows, copyText = "$kcal kcal", iconName = "ic_food", big = "$kcal kcal",
            nums = listOf(p.kcal, p.protein, p.carbs, p.fat, p.fibre, grams), unit = "kcal",
            caption = if (estimated) "APPROX · PORTION SIZE ESTIMATED" else "APPROX · STANDARD FOOD TABLES",
            timeoutSeconds = 25
        )
    }

    // ---- Open Food Facts, by barcode

    private fun lookupBarcode(code: String): InspectResult? {
        val fields = "product_name,brands,quantity,serving_size,serving_quantity,nutriments"
        val r = Http.get("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=$fields", 4000)
        if (!r.ok) return null
        return buildPackaged(r.body)
    }

    internal fun buildPackaged(body: String): InspectResult? {
        val o = try { JSONObject(body) } catch (_: Exception) { return null }
        if (o.optInt("status", 0) != 1) return null
        val prod = o.optJSONObject("product") ?: return null
        val n = prod.optJSONObject("nutriments") ?: return null
        val nan = Double.NaN
        var kcal = n.optDouble("energy-kcal_100g", nan)
        if (kcal.isNaN()) {
            val kj = n.optDouble("energy-kj_100g", nan)
            if (!kj.isNaN()) kcal = kj / 4.184
        }
        val protein = n.optDouble("proteins_100g", nan)
        val carbs = n.optDouble("carbohydrates_100g", nan)
        val fat = n.optDouble("fat_100g", nan)
        if (kcal.isNaN() && protein.isNaN() && carbs.isNaN() && fat.isNaN()) return null
        val fibre = n.optDouble("fiber_100g", nan)
        val sugars = n.optDouble("sugars_100g", nan)
        var salt = n.optDouble("salt_100g", nan)
        if (salt.isNaN()) n.optDouble("sodium_100g", nan).let { if (!it.isNaN()) salt = it * 2.5 }
        val name = prod.optString("product_name", "").ifBlank { "Packaged food" }
        val brand = prod.optString("brands", "").substringBefore(',').trim()
        val k = if (kcal.isNaN()) (protein.orZero() * 4 + carbs.orZero() * 4 + fat.orZero() * 9) else kcal

        val rows = ArrayList<Pair<String, String>>()
        fun add(label: String, v: Double, unit: String = "g") { if (!v.isNaN()) rows.add(label to "${num(v)} $unit") }
        add("Protein", protein); add("Carbs", carbs); add("of which sugars", sugars); add("Fat", fat); add("Fibre", fibre); add("Salt", salt)
        val servingG = prod.optDouble("serving_quantity", nan)
        if (!servingG.isNaN() && servingG > 0) rows.add("Per serving (${num(servingG)} g)" to "${Math.round(k * servingG / 100.0)} kcal")
        rows.add("Pack size" to prod.optString("quantity", "").ifBlank { "" })

        val kc = Math.round(k).toInt()
        return InspectResult(
            domain = "FOOD", pill = "$kc kcal", title = name, cardTitle = "$kc kcal",
            cardText = listOf(brand, "per 100 g").filter { it.isNotBlank() }.joinToString(" · "),
            subtitle = listOf(name, brand).filter { it.isNotBlank() }.joinToString(" · ") + " · per 100 g",
            rows = rows.filter { it.second.isNotBlank() }, copyText = "$kc kcal", iconName = "ic_food", big = "$kc kcal",
            nums = listOf(k, protein.orZero(), carbs.orZero(), fat.orZero(), fibre.orZero(), 100.0), unit = "kcal",
            caption = "OPEN FOOD FACTS · PER 100 G", timeoutSeconds = 25
        )
    }

    private fun Double.orZero() = if (isNaN()) 0.0 else this
}
