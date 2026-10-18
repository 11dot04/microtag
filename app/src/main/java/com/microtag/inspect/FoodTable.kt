package com.microtag.inspect

import java.util.Locale

/** One food, per 100 g, plus how much one usual serving weighs. */
internal class Food(
    val name: String,
    val kcal: Double, val protein: Double, val carbs: Double, val fat: Double, val fibre: Double,
    val servingG: Double, val servingUnit: String, val cupG: Double
)

/** What was asked for: [qty] of [unit] (null = servings or pieces) of [food]. */
internal class FoodQuery(val food: Food, val qty: Double, val unit: String?, val explicit: Boolean)

/** The nutrition of a portion. */
internal class Portion(val grams: Double, val kcal: Double, val protein: Double, val carbs: Double, val fat: Double, val fibre: Double)

internal object FoodTable {
    private val byAlias: Map<String, Food> by lazy { parseTable(FOOD_DATA) }
    val foods: Collection<Food> get() = byAlias.values.toSet()

    /** Parses the table text; every name and alias maps to its food. A repeated alias keeps the first. */
    fun parseTable(data: String): Map<String, Food> {
        val out = LinkedHashMap<String, Food>()
        for (line in data.lines()) {
            val f = line.trim().split('|')
            if (f.size < 10) continue
            val food = try {
                Food(f[0].trim(), f[2].toDouble(), f[3].toDouble(), f[4].toDouble(), f[5].toDouble(), f[6].toDouble(), f[7].toDouble(), f[8].trim(), f[9].toDouble())
            } catch (_: NumberFormatException) {
                continue
            }
            out.putIfAbsent(f[0].trim().lowercase(Locale.ROOT), food)
            f[1].split(',').map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.forEach { out.putIfAbsent(it, food) }
        }
        return out
    }

    fun find(name: String): Food? {
        val n = name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        if (n.isEmpty()) return null
        byAlias[n]?.let { return it }
        // Plurals the table doesn't list: "bananas", "tomatoes", "berries".
        val singulars = buildList {
            if (n.endsWith("ies")) add(n.dropLast(3) + "y")
            if (n.endsWith("oes")) add(n.dropLast(2))
            if (n.endsWith("es")) add(n.dropLast(2))
            if (n.endsWith("s")) add(n.dropLast(1))
        }
        return singulars.firstNotNullOfOrNull { byAlias[it] }
    }

    // ---- reading "2 rotis", "100g paneer", "1 cup rice", "half a banana", "banana"

    private val unitWords = mapOf(
        "g" to "g", "gm" to "g", "gms" to "g", "gram" to "g", "grams" to "g", "grm" to "g",
        "kg" to "kg", "kgs" to "kg", "kilo" to "kg", "kilos" to "kg", "kilogram" to "kg", "kilograms" to "kg",
        "ml" to "ml", "l" to "l", "litre" to "l", "litres" to "l", "liter" to "l", "liters" to "l",
        "oz" to "oz", "ounce" to "oz", "ounces" to "oz", "lb" to "lb", "lbs" to "lb", "pound" to "lb", "pounds" to "lb",
        "cup" to "cup", "cups" to "cup", "tbsp" to "tbsp", "tablespoon" to "tbsp", "tablespoons" to "tbsp",
        "tsp" to "tsp", "teaspoon" to "tsp", "teaspoons" to "tsp",
        "piece" to "piece", "pieces" to "piece", "pc" to "piece", "pcs" to "piece", "slice" to "slice", "slices" to "slice",
        "bowl" to "bowl", "bowls" to "bowl", "katori" to "katori", "katoris" to "katori", "glass" to "glass", "glasses" to "glass",
        "plate" to "plate", "plates" to "plate", "serving" to "serving", "servings" to "serving",
        "handful" to "handful", "handfuls" to "handful", "scoop" to "scoop", "scoops" to "scoop", "can" to "can", "cans" to "can"
    )
    private val numberWords = mapOf(
        "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0, "six" to 6.0,
        "half" to 0.5, "quarter" to 0.25, "½" to 0.5, "¼" to 0.25, "¾" to 0.75
    )
    private val lead = Regex("""^(\d+\s*/\s*\d+|\d+(?:[.,]\d+)?|[½¼¾])\s*([a-zA-Z]*)\s*(.*)$""")

    private fun number(raw: String): Double? {
        numberWords[raw]?.let { return it }
        if (raw.contains('/')) {
            val (a, b) = raw.split('/').map { it.trim().toDoubleOrNull() ?: return null }
            return if (b == 0.0) null else a / b
        }
        return raw.replace(',', '.').toDoubleOrNull()
    }

    fun parse(text: String): FoodQuery? {
        var t = text.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trimEnd('.', ',', '!')
        if (t.isEmpty() || t.length > 60) return null
        var qty = 1.0
        var unit: String? = null
        var explicit = false

        // "half a banana", "one egg": a number word first.
        val first = t.substringBefore(' ')
        var rest = t
        if (first in numberWords && t.contains(' ')) {
            qty = numberWords.getValue(first)
            rest = t.substringAfter(' ')
            explicit = true
            if (first == "half" || first == "quarter") rest = rest.removePrefix("a ").removePrefix("of ").removePrefix("a ")
        } else {
            val m = lead.find(t)
            if (m != null) {
                qty = number(m.groupValues[1]) ?: return null
                explicit = true
                val word = m.groupValues[2]
                val remainder = m.groupValues[3]
                if (word.isNotEmpty() && word in unitWords) {
                    unit = unitWords.getValue(word)
                    rest = remainder
                } else {
                    // The word after the number was part of the food ("2 rotis"), unless it was glued to the number ("2roti").
                    rest = (word + " " + remainder).trim()
                }
            }
        }
        rest = rest.removePrefix("of ").trim()
        // "half a cup of rice"
        if (rest.startsWith("a ") || rest.startsWith("an ")) rest = rest.substringAfter(' ')
        val w = rest.substringBefore(' ')
        if (unit == null && w in unitWords && rest.contains(' ')) {
            unit = unitWords.getValue(w)
            rest = rest.substringAfter(' ').removePrefix("of ").trim()
        }
        if (qty <= 0.0 || qty > 5000.0) return null
        val food = find(rest) ?: return null
        return FoodQuery(food, qty, unit, explicit)
    }

    /** Grams in the portion asked for. Unknown cups and bowls are estimated from the serving, which the card says. */
    fun grams(q: FoodQuery): Double {
        val f = q.food
        val n = q.qty
        return when (q.unit) {
            "g" -> n
            "kg" -> n * 1000.0
            "ml" -> n
            "l" -> n * 1000.0
            "oz" -> n * 28.3495
            "lb" -> n * 453.592
            "cup" -> n * (if (f.cupG > 0) f.cupG else f.servingG)
            "tbsp" -> n * (when (f.servingUnit) { "tbsp" -> f.servingG; "tsp" -> f.servingG * 3 else -> 15.0 })
            "tsp" -> n * (when (f.servingUnit) { "tsp" -> f.servingG; "tbsp" -> f.servingG / 3 else -> 5.0 })
            "bowl" -> n * f.servingG * 1.5
            "plate" -> n * f.servingG * 2.0
            else -> n * f.servingG
        }
    }

    fun portion(f: Food, grams: Double): Portion {
        val k = grams / 100.0
        return Portion(grams, f.kcal * k, f.protein * k, f.carbs * k, f.fat * k, f.fibre * k)
    }

    /** Valid EAN-8, UPC-A, EAN-13 and GTIN-14 numbers: the last digit checks the rest. */
    fun validGtin(digits: String): Boolean {
        if (digits.length !in setOf(8, 12, 13, 14) || !digits.all { it.isDigit() }) return false
        var sum = 0
        for (i in 0 until digits.length - 1) {
            val d = digits[digits.length - 2 - i] - '0'
            sum += if (i % 2 == 0) d * 3 else d
        }
        return (10 - sum % 10) % 10 == digits.last() - '0'
    }
}
