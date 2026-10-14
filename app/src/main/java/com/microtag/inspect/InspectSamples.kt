package com.microtag.inspect

import java.time.LocalDate

/** Example cards for the Inspect screen's showcase. Nothing here touches the network. */
object InspectSamples {

    fun calc() = InspectPayload(
        domain = "CALC", title = "25,185", subtitle = "345*73", copyText = "25185",
        rows = listOf("Expression" to "345*73", "Result" to "25185")
    )

    fun timespan(): InspectPayload {
        val today = LocalDate.now()
        val d = today.plusDays(120)
        return InspectPayload(
            domain = "DATE", title = "In 120 days", subtitle = d.toString(), copyText = d.toString(),
            rows = listOf(
                "Date" to d.toString(), "Breakdown" to "3 months, 4 weeks, 2 days",
                "Week" to "Week ${d.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)} of ${d.year}",
                "Day of year" to d.dayOfYear.toString()
            ),
            nums = listOf(today.toEpochDay().toDouble(), d.toEpochDay().toDouble())
        )
    }

    fun time() = InspectPayload(
        domain = "TIME", title = "10:30 pm IST", subtitle = "From 5 PM GMT", copyText = "10:30 pm IST",
        rows = listOf("Local" to "Today · 10:30 pm IST", "That is" to "in 3h 10m"),
        nums = listOf(17 * 60.0, 22 * 60.0 + 30)
    )

    fun convert() = InspectPayload(
        domain = "CONV", title = "23.13 kg", subtitle = "51 lb", copyText = "23.13 kg",
        rows = listOf("In g" to "23133.2 g", "In st" to "3.64 st")
    )

    fun money() = InspectPayload(
        domain = "MONEY", title = "₹3,681.00", subtitle = "\$38.00", copyText = "₹3,681.00",
        rows = listOf("In INR" to "₹3,681.00", "Rate" to "1 USD = 96.87 INR", "Updated" to "2 h ago")
    )

    fun colour() = InspectPayload(
        domain = "COLOUR", title = "Volt", big = "#CCFF00", subtitle = "rgb(204, 255, 0)", copyText = "#CCFF00",
        accent = 0xFFCCFF00.toInt(),
        palette = listOf(0xFFCCFF00.toInt(), 0xFF3300FF.toInt()),
        rows = listOf(
            "Name" to "Volt", "HEX" to "#CCFF00", "RGB" to "rgb(204, 255, 0)", "HSL" to "hsl(72, 100%, 50%)",
            "Readable with" to "Black text, 17.9:1", "Complement" to "#3300FF"
        )
    )

    fun define() = InspectPayload(
        domain = "DEFINE", title = "raster", subtitle = "/ˈɹæstɚ/", copyText = "raster",
        url = "https://en.wiktionary.org/wiki/raster",
        rows = listOf(
            "noun" to "A scanning pattern of parallel lines that form the display of an image projected on a cathode-ray tube, computer monitor or video display terminal.",
            "" to "A bitmap image, consisting of a grid of pixels, stored as a sequence of values.",
            "" to "A rectangular pattern of parallel scanning lines followed by the electron beam on a screen.",
            "verb" to "To scan in parallel lines.",
            "" to "To convert a vector image into a bitmap.",
            "Source" to "Wiktionary, Datamuse"
        )
    )

    fun wiki() = InspectPayload(
        domain = "WIKI", title = "Alan Turing", subtitle = "English mathematician and computer scientist (1912–1954)",
        copyText = "Alan Turing", url = "https://en.wikipedia.org/wiki/Alan_Turing",
        fullContent = "Alan Mathison Turing was an English mathematician, computer scientist, logician, cryptanalyst, philosopher and theoretical biologist. He was highly influential in the development of theoretical computer science."
    )

    fun link() = InspectPayload(
        domain = "URL", title = "bit.ly/3xYzAb", subtitle = "Shortened link", copyText = "https://bit.ly/3xYzAb",
        url = "https://bit.ly/3xYzAb", status = "WARN", checkHost = "bit.ly", resolveUrl = "https://bit.ly/3xYzAb",
        rows = listOf("Heads up" to "A shortened link hides where it goes. Resolve it to see the real address.")
    )

    fun upi() = InspectPayload(
        domain = "UPI", title = "ryan@oksbi", subtitle = "Google Pay · SBI", copyText = "ryan@oksbi",
        url = "upi://pay?pa=ryan@oksbi&cu=INR"
    )

    fun bank() = InspectPayload(
        domain = "IFSC", title = "SBIN0001234", subtitle = "FORT, MUMBAI", copyText = "SBIN0001234",
        rows = listOf(
            "Bank" to "State Bank of India", "Branch" to "Fort", "Place" to "Mumbai, Maharashtra",
            "MICR" to "400002001", "Supports" to "UPI, IMPS, NEFT, RTGS"
        )
    )

    fun post() = InspectPayload(
        domain = "PIN", title = "400001", subtitle = "Mumbai, Maharashtra", copyText = "400001",
        rows = listOf("Area" to "Fort", "District" to "Mumbai", "State" to "Maharashtra", "Post offices" to "Fort, Horniman Circle, GPO and 4 more")
    )

    fun number() = InspectPayload(
        domain = "NUM", title = "34,54,64,000", subtitle = "Number in words", copyText = "345464000",
        rows = listOf(
            "In words" to "Thirty-four crore fifty-four lakh sixty-four thousand",
            "International" to "three hundred forty-five million four hundred sixty-four thousand",
            "Compact" to "345.46 M"
        )
    )

    fun dimensions() = InspectPayload(
        domain = "DIMS", title = "384,000 cm³", big = "120 × 80 × 40 cm", subtitle = "Volume", copyText = "120 × 80 × 40 cm",
        nums = listOf(120.0, 80.0, 40.0), unit = "cm",
        rows = listOf("Area" to "9600 cm²", "Volume" to "384000 cm³", "Space diagonal" to "149.7 cm", "In in" to "47.24 × 31.5 × 15.75 in")
    )
}
