package com.microtag.inspect

import java.util.Locale
import kotlin.math.abs

/**
 * Standard sizes, in both directions:
 *  - "A3", "us letter", "1080p", "queen bed", "credit card" -> the dimensions ([find]).
 *  - 210 x 297 mm, 8.27 x 11.69 in -> "A4 paper" ([match]).
 *
 * Only sizes that are defined by a standard or have been stable for years are listed.
 * Bare names that would fire on ordinary words ("letter", "legal", "4K", "2x4", "DL") are not aliases;
 * they need a qualifier ("us letter", "4K UHD", "2x4 lumber", "DL envelope").
 */
internal object DimensionStandards {

    class Std(
        val name: String,
        /** Descriptive name used in subtitles: "A4 paper". */
        val label: String,
        val aliases: List<String>,
        /** Width, height (and depth) in [unit]. */
        val dims: List<Double>,
        val unit: String,
        val group: String,
        val note: String = ""
    ) {
        val px: Boolean get() = unit == "px"
    }

    private val all = ArrayList<Std>()

    private fun s(
        name: String, aliases: String, dims: List<Double>, unit: String, group: String,
        note: String = "", label: String = name
    ) {
        all.add(
            Std(
                name, label,
                aliases.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                dims, unit, group, note
            )
        )
    }

    init {
        // ---- ISO 216 and ISO 269 -------------------------------------------------------------
        val a = listOf(841 to 1189, 594 to 841, 420 to 594, 297 to 420, 210 to 297, 148 to 210, 105 to 148, 74 to 105, 52 to 74, 37 to 52, 26 to 37)
        a.forEachIndexed { i, (w, h) ->
            s("A$i", "a$i,a$i paper,a$i size,a$i sheet,iso a$i,din a$i,a$i page", listOf(w.toDouble(), h.toDouble()), "mm", "ISO 216 paper", label = "A$i paper")
        }
        val b = listOf(1000 to 1414, 707 to 1000, 500 to 707, 353 to 500, 250 to 353, 176 to 250, 125 to 176, 88 to 125, 62 to 88, 44 to 62, 31 to 44)
        b.forEachIndexed { i, (w, h) ->
            s("B$i", "b$i,b$i paper,b$i size,b$i sheet,iso b$i", listOf(w.toDouble(), h.toDouble()), "mm", "ISO 216 paper",
                note = "ISO B. Japan's JIS B series is a little larger.", label = "B$i paper")
        }
        val c = listOf(917 to 1297, 648 to 917, 458 to 648, 324 to 458, 229 to 324, 162 to 229, 114 to 162)
        c.forEachIndexed { i, (w, h) ->
            s("C$i", "c$i,c$i envelope,c$i size,iso c$i", listOf(w.toDouble(), h.toDouble()), "mm", "ISO 269 envelope",
                note = "Fits an A${i} sheet unfolded.", label = "C$i envelope")
        }
        s("DL", "dl envelope,dl size,iso dl,dl card", listOf(110.0, 220.0), "mm", "ISO 269 envelope", "Fits an A4 sheet folded in three.", "DL envelope")

        // ---- North American paper ----------------------------------------------------------
        s("US Letter", "us letter,letter size,letter paper,letter sheet,us letter paper,ansi a,ansi a paper", listOf(8.5, 11.0), "in", "US paper",
            "Also ANSI A.", "US Letter paper")
        s("US Legal", "us legal,legal size,legal paper,legal sheet,us legal paper", listOf(8.5, 14.0), "in", "US paper", label = "US Legal paper")
        s("Tabloid", "tabloid paper,tabloid size,ledger paper,ansi b,ansi b paper", listOf(11.0, 17.0), "in", "US paper",
            "Also called Ledger when turned landscape. Also ANSI B.", "Tabloid paper")
        s("Executive", "executive paper,executive size,us executive", listOf(7.25, 10.5), "in", "US paper", label = "Executive paper")
        s("Statement", "statement paper,half letter,half letter paper,statement size", listOf(5.5, 8.5), "in", "US paper", label = "Statement paper")
        s("Junior Legal", "junior legal,jr legal,junior legal pad", listOf(5.0, 8.0), "in", "US paper", label = "Junior Legal paper")
        s("ANSI C", "ansi c,ansi c paper", listOf(17.0, 22.0), "in", "ANSI drawing", label = "ANSI C sheet")
        s("ANSI D", "ansi d,ansi d paper", listOf(22.0, 34.0), "in", "ANSI drawing", label = "ANSI D sheet")
        s("ANSI E", "ansi e,ansi e paper", listOf(34.0, 44.0), "in", "ANSI drawing", label = "ANSI E sheet")
        s("ARCH A", "arch a,architectural a", listOf(9.0, 12.0), "in", "Architectural drawing", label = "ARCH A sheet")
        s("ARCH B", "arch b,architectural b", listOf(12.0, 18.0), "in", "Architectural drawing", label = "ARCH B sheet")
        s("ARCH C", "arch c,architectural c", listOf(18.0, 24.0), "in", "Architectural drawing", label = "ARCH C sheet")
        s("ARCH D", "arch d,architectural d", listOf(24.0, 36.0), "in", "Architectural drawing", label = "ARCH D sheet")
        s("ARCH E", "arch e,architectural e", listOf(36.0, 48.0), "in", "Architectural drawing", label = "ARCH E sheet")
        s("ARCH E1", "arch e1,architectural e1", listOf(30.0, 42.0), "in", "Architectural drawing", label = "ARCH E1 sheet")
        s("Foolscap", "foolscap,foolscap paper,foolscap folio", listOf(8.0, 13.0), "in", "Paper", "Old British size, still sold in India.", "Foolscap paper")
        s("#10 envelope", "number 10 envelope,no 10 envelope,#10 envelope,business envelope", listOf(4.125, 9.5), "in", "US envelope", label = "#10 envelope")
        s("Index card", "index card,3x5 index card,index card 3x5", listOf(3.0, 5.0), "in", "Stationery", label = "Index card")
        s("Poker card", "poker card,playing card,poker playing card", listOf(2.5, 3.5), "in", "Stationery", label = "Poker playing card")
        s("Postcard", "us postcard,postcard size", listOf(4.0, 6.0), "in", "Stationery", "A6 (105 × 148 mm) elsewhere.", "US postcard")

        // ---- Photo prints --------------------------------------------------------------------
        s("3R", "3r,3r print,3r photo,3.5x5 photo", listOf(3.5, 5.0), "in", "Photo print", label = "3R photo print")
        s("4R", "4r,4r print,4r photo,4x6 photo,4x6 print,4 by 6 photo", listOf(4.0, 6.0), "in", "Photo print", "The usual lab print.", "4 × 6 photo print (4R)")
        s("5R", "5r,5r print,5r photo,5x7 photo,5x7 print", listOf(5.0, 7.0), "in", "Photo print", label = "5 × 7 photo print (5R)")
        s("6R", "6r,6r print,6r photo,6x8 photo,6x8 print", listOf(6.0, 8.0), "in", "Photo print", label = "6 × 8 photo print (6R)")
        s("8R", "8r,8r print,8r photo,8x10 photo,8x10 print", listOf(8.0, 10.0), "in", "Photo print", label = "8 × 10 photo print (8R)")
        s("S8R", "s8r,8x12 photo,8x12 print", listOf(8.0, 12.0), "in", "Photo print", label = "8 × 12 photo print (S8R)")
        s("11 × 14", "11x14 photo,11x14 print,11x14 frame", listOf(11.0, 14.0), "in", "Photo print", label = "11 × 14 print")
        s("16 × 20", "16x20 photo,16x20 print,16x20 frame", listOf(16.0, 20.0), "in", "Photo print", label = "16 × 20 print")
        s("20 × 30", "20x30 photo,20x30 print,20x30 poster", listOf(20.0, 30.0), "in", "Photo print", label = "20 × 30 print")

        // ---- Screens -------------------------------------------------------------------------
        s("480p", "480p,480p resolution,sd 480p", listOf(854.0, 480.0), "px", "Screen", "Widescreen standard definition.", "480p video")
        s("720p", "720p,720p resolution,hd ready,hd 720p", listOf(1280.0, 720.0), "px", "Screen", label = "HD 720p")
        s("1080p", "1080p,1080p resolution,full hd,fhd,full hd resolution", listOf(1920.0, 1080.0), "px", "Screen", label = "Full HD 1080p")
        s("1440p", "1440p,1440p resolution,qhd,wqhd,quad hd", listOf(2560.0, 1440.0), "px", "Screen", label = "QHD 1440p")
        s("4K UHD", "4k uhd,uhd,ultra hd,2160p,4k resolution,4k screen,4k tv", listOf(3840.0, 2160.0), "px", "Screen", label = "4K UHD")
        s("DCI 4K", "dci 4k,cinema 4k,dci4k", listOf(4096.0, 2160.0), "px", "Screen", "Digital cinema.", "DCI 4K")
        s("5K", "5k resolution,5k screen,5k display", listOf(5120.0, 2880.0), "px", "Screen", label = "5K")
        s("8K UHD", "8k uhd,8k resolution,8k screen,4320p", listOf(7680.0, 4320.0), "px", "Screen", label = "8K UHD")
        s("qHD", "qhd 540p,qhd 960x540", listOf(960.0, 540.0), "px", "Screen", label = "qHD")
        s("VGA", "vga,vga resolution", listOf(640.0, 480.0), "px", "Screen", label = "VGA")
        s("SVGA", "svga,svga resolution", listOf(800.0, 600.0), "px", "Screen", label = "SVGA")
        s("XGA", "xga,xga resolution", listOf(1024.0, 768.0), "px", "Screen", label = "XGA")
        s("WXGA", "wxga,wxga resolution", listOf(1280.0, 800.0), "px", "Screen", "Many laptops use 1366 × 768 and call it WXGA too.", "WXGA")
        s("SXGA", "sxga,sxga resolution", listOf(1280.0, 1024.0), "px", "Screen", label = "SXGA")
        s("HD+", "hd+,hd plus,hd+ resolution", listOf(1600.0, 900.0), "px", "Screen", label = "HD+")
        s("UXGA", "uxga,uxga resolution", listOf(1600.0, 1200.0), "px", "Screen", label = "UXGA")
        s("WUXGA", "wuxga,wuxga resolution", listOf(1920.0, 1200.0), "px", "Screen", label = "WUXGA")
        s("WQXGA", "wqxga,wqxga resolution", listOf(2560.0, 1600.0), "px", "Screen", label = "WQXGA")

        // ---- Social (sizes that have stayed put) --------------------------------------------------
        s("Instagram square", "instagram square,instagram post,instagram square post", listOf(1080.0, 1080.0), "px", "Social", label = "Instagram square post")
        s("Instagram portrait", "instagram portrait,instagram portrait post", listOf(1080.0, 1350.0), "px", "Social", label = "Instagram portrait post")
        s("Instagram story", "instagram story,instagram reel,instagram stories,instagram reels", listOf(1080.0, 1920.0), "px", "Social", label = "Instagram story or reel")
        s("YouTube thumbnail", "youtube thumbnail,yt thumbnail", listOf(1280.0, 720.0), "px", "Social", label = "YouTube thumbnail")
        s("X header", "x header,twitter header,x banner,twitter banner", listOf(1500.0, 500.0), "px", "Social", label = "X (Twitter) header")
        s("LinkedIn cover", "linkedin cover,linkedin banner,linkedin background", listOf(1584.0, 396.0), "px", "Social", label = "LinkedIn cover")

        // ---- Cards and photos ---------------------------------------------------------------
        s("ID-1 card", "credit card,debit card,atm card,bank card,id-1,id1,id 1 card,credit card size,id card", listOf(85.6, 53.98), "mm", "Card",
            "ISO/IEC 7810 ID-1: bank cards, driving licences, most ID cards.", "Credit card (ID-1)")
        s("ID-2 card", "id-2,id2,id 2 card", listOf(105.0, 74.0), "mm", "Card", label = "ID-2 card")
        s("ICAO TD3", "td3,icao td3,passport booklet,passport size,passport book", listOf(125.0, 88.0), "mm", "Card", "The passport booklet. Same as ID-3.", "Passport booklet (TD3)")
        s("Business card", "business card,us business card,visiting card business", listOf(3.5, 2.0), "in", "Card",
            "Europe: 85 × 55 mm. India: 90 × 54 mm.", "US business card")
        s("Passport photo", "passport photo,passport photo size,passport size photo,uk passport photo,india passport photo,eu passport photo", listOf(35.0, 45.0), "mm", "Photo",
            "UK, EU and India. US passports use 2 × 2 in.", "Passport photo (35 × 45 mm)")
        s("US passport photo", "us passport photo,us visa photo,2x2 photo", listOf(2.0, 2.0), "in", "Photo", label = "US passport photo")

        // ---- Beds (US) --------------------------------------------------------------------------
        s("Twin bed", "twin bed,twin mattress,twin size bed,single bed us", listOf(38.0, 75.0), "in", "Bed", label = "US twin bed")
        s("Twin XL bed", "twin xl bed,twin xl mattress,twin xl", listOf(38.0, 80.0), "in", "Bed", label = "US twin XL bed")
        s("Full bed", "full bed,full mattress,full size bed,double bed us", listOf(54.0, 75.0), "in", "Bed", label = "US full bed")
        s("Queen bed", "queen bed,queen mattress,queen size bed,queen size mattress", listOf(60.0, 80.0), "in", "Bed", label = "US queen bed")
        s("King bed", "king bed,king mattress,king size bed,king size mattress", listOf(76.0, 80.0), "in", "Bed", label = "US king bed")
        s("California king bed", "california king,cal king,cal king bed,california king bed", listOf(72.0, 84.0), "in", "Bed", label = "California king bed")

        // ---- Sports surfaces -------------------------------------------------------------------
        s("Football pitch", "football pitch,soccer field,soccer pitch,football field fifa", listOf(105.0, 68.0), "m", "Sport", "The common international size. Allowed range is wider.", "Football (soccer) pitch")
        s("Basketball court", "basketball court,nba court,nba basketball court", listOf(28.65, 15.24), "m", "Sport", "NBA: 94 × 50 ft. FIBA: 28 × 15 m.", "Basketball court (NBA)")
        s("Tennis court", "tennis court,tennis court doubles", listOf(23.77, 10.97), "m", "Sport", "Doubles width. Singles is 8.23 m wide.", "Tennis court (doubles)")
        s("Badminton court", "badminton court,badminton court doubles", listOf(13.4, 6.1), "m", "Sport", "Doubles. Singles is 5.18 m wide.", "Badminton court (doubles)")
        s("Volleyball court", "volleyball court", listOf(18.0, 9.0), "m", "Sport", label = "Volleyball court")
        s("Cricket pitch", "cricket pitch,cricket wicket,22 yards pitch", listOf(20.12, 3.05), "m", "Sport", "22 yards long.", "Cricket pitch")
        s("American football field", "american football field,nfl field,gridiron", listOf(109.73, 48.77), "m", "Sport", "120 × 53⅓ yd with end zones.", "American football field")
        s("Olympic pool", "olympic pool,olympic swimming pool,olympic size pool", listOf(50.0, 25.0), "m", "Sport", "Eight to ten lanes, at least 2 m deep.", "Olympic swimming pool")
        s("Table tennis table", "table tennis table,ping pong table", listOf(2.74, 1.525), "m", "Sport", label = "Table tennis table")
        s("Snooker table", "snooker table,full size snooker table,12ft snooker table", listOf(12.0, 6.0), "ft", "Sport", "Outer size. The playing area is about 11 ft 8½ in × 5 ft 10 in.", "Snooker table (12 ft)")
        s("Pool table 9 ft", "9 ft pool table,9ft pool table,pool table size", listOf(100.0, 50.0), "in", "Sport", "Playing surface. 8 ft is 88 × 44 in, 7 ft is 78 × 39 in.", "9 ft pool table")
        s("Pickleball court", "pickleball court", listOf(44.0, 20.0), "ft", "Sport", label = "Pickleball court")
        s("Squash court", "squash court", listOf(9.75, 6.4), "m", "Sport", label = "Squash court")
        s("Kabaddi court", "kabaddi court,kabaddi ground", listOf(13.0, 10.0), "m", "Sport", "Men's size. Women's is 12 × 8 m.", "Kabaddi court")
        s("Ice hockey rink", "ice hockey rink,nhl rink,hockey rink", listOf(200.0, 85.0), "ft", "Sport", label = "Ice hockey rink (NHL)")
        s("Football goal", "football goal,soccer goal,soccer goal size", listOf(7.32, 2.44), "m", "Sport", label = "Football goal")

        // ---- Shipping and storage ---------------------------------------------------------------
        s("20 ft container", "20ft container,20 ft container,20 foot container,teu,20ft shipping container", listOf(6.058, 2.438, 2.591), "m", "Shipping", "External size. One TEU.", "20 ft shipping container")
        s("40 ft container", "40ft container,40 ft container,40 foot container,feu,40ft shipping container", listOf(12.192, 2.438, 2.591), "m", "Shipping", "External size. Two TEU.", "40 ft shipping container")
        s("40 ft high cube", "40ft high cube,40 ft high cube,40ft hc,40 foot high cube", listOf(12.192, 2.438, 2.896), "m", "Shipping", "External size.", "40 ft high-cube container")
        s("Euro pallet", "euro pallet,eur pallet,epal pallet,eur 1 pallet", listOf(1200.0, 800.0), "mm", "Shipping", label = "EUR pallet")
        s("US pallet", "us pallet,gma pallet,standard us pallet,north american pallet", listOf(48.0, 40.0), "in", "Shipping", "GMA pallet.", "US (GMA) pallet")

        // ---- Building -------------------------------------------------------------------------
        s("2 × 4", "2x4 lumber,2x4 stud,2x4 board,2 by 4 lumber,two by four,2x4 timber,2×4 lumber", listOf(1.5, 3.5), "in", "Lumber", "Nominal 2 × 4. Real size after planing.", "2 × 4 lumber (actual)")
        s("2 × 6", "2x6 lumber,2x6 board,2 by 6 lumber,two by six,2x6 timber", listOf(1.5, 5.5), "in", "Lumber", "Nominal 2 × 6.", "2 × 6 lumber (actual)")
        s("2 × 8", "2x8 lumber,2x8 board,2 by 8 lumber,two by eight", listOf(1.5, 7.25), "in", "Lumber", "Nominal 2 × 8.", "2 × 8 lumber (actual)")
        s("2 × 10", "2x10 lumber,2x10 board,2 by 10 lumber,two by ten", listOf(1.5, 9.25), "in", "Lumber", "Nominal 2 × 10.", "2 × 10 lumber (actual)")
        s("2 × 12", "2x12 lumber,2x12 board,2 by 12 lumber,two by twelve", listOf(1.5, 11.25), "in", "Lumber", "Nominal 2 × 12.", "2 × 12 lumber (actual)")
        s("4 × 4", "4x4 lumber,4x4 post,4 by 4 lumber,four by four", listOf(3.5, 3.5), "in", "Lumber", "Nominal 4 × 4.", "4 × 4 post (actual)")
        s("1 × 4", "1x4 lumber,1x4 board,1 by 4 lumber,one by four", listOf(0.75, 3.5), "in", "Lumber", "Nominal 1 × 4.", "1 × 4 board (actual)")
        s("1 × 6", "1x6 lumber,1x6 board,1 by 6 lumber,one by six", listOf(0.75, 5.5), "in", "Lumber", "Nominal 1 × 6.", "1 × 6 board (actual)")
        s("Plywood sheet", "plywood sheet,4x8 plywood,4x8 sheet,4x8 sheet of plywood", listOf(4.0, 8.0), "ft", "Building", "1220 × 2440 mm in metric countries.", "Plywood sheet (4 × 8 ft)")
        s("Indian modular brick", "indian brick,indian modular brick,modular brick,standard brick india", listOf(190.0, 90.0, 90.0), "mm", "Building", "Nominal 200 × 100 × 100 mm with mortar.", "Indian modular brick")
        s("UK brick", "uk brick,british brick,standard brick uk,uk standard brick", listOf(215.0, 102.5, 65.0), "mm", "Building", label = "UK standard brick")
        s("US modular brick", "us brick,us modular brick,american brick", listOf(7.625, 3.625, 2.25), "in", "Building", label = "US modular brick")
        s("Parking space", "parking space size,parking spot size,parking bay size,car parking space size", listOf(9.0, 18.0), "ft", "Building", "Common US size. Indian norms use about 2.5 × 5 m.", "Parking space")
        s("Yoga mat", "yoga mat size,standard yoga mat", listOf(24.0, 68.0), "in", "Other", "Most are 61 × 173 cm.", "Yoga mat")
    }

    // ---- lookup by name ----------------------------------------------------------------------

    private fun norm(s: String): String =
        s.lowercase(Locale.ROOT).replace('×', 'x').replace('-', ' ').replace(Regex("""[^a-z0-9+#.\s]"""), " ")
            .replace(Regex("\\s+"), " ").trim().trimEnd('.')

    private val byAlias: Map<String, Std> by lazy {
        val m = LinkedHashMap<String, Std>()
        // First registration wins, so the order above is the tie-break (ISO before ANSI, and so on).
        val bareSize = Regex("""^[\d.]+ x [\d.]+$""")   // "2 x 4" is arithmetic, not a lookup
        for (e in all) {
            for (a in e.aliases + e.name + e.label) {
                val k = norm(a)
                if (!bareSize.matches(k)) m.putIfAbsent(k, e)
            }
        }
        m
    }

    fun find(text: String): Std? {
        val t = norm(text)
        if (t.length < 2 || t.length > 40) return null
        return byAlias[t]
    }

    // ---- lookup by size ----------------------------------------------------------------------

    private fun metresPer(unit: String): Double = when (unit) {
        "mm" -> 0.001
        "cm" -> 0.01
        "m" -> 1.0
        "in" -> 0.0254
        "ft" -> 0.3048
        else -> 1.0
    }

    /** The closest standard within [tol] on every side (turned either way), or null. */
    fun match(values: List<Double>, unit: String, tol: Double = 0.01): Std? {
        if (values.size < 2) return null
        val px = unit == "px"
        val mine = values.map { it * metresPer(unit) }.sortedDescending()
        var best: Std? = null
        var bestErr = Double.MAX_VALUE
        for (e in all) {
            if (e.px != px || e.dims.size != values.size) continue
            val theirs = e.dims.map { it * metresPer(e.unit) }.sortedDescending()
            var err = 0.0
            for (i in mine.indices) err = maxOf(err, abs(mine[i] - theirs[i]) / theirs[i])
            if (err <= tol && err < bestErr - 1e-12) {
                best = e
                bestErr = err
            }
        }
        return best
    }

    val size: Int get() = all.size
}
