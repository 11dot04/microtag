package com.microtag.inspect

import java.io.Serializable

/**
 * What the detail overlay shows. One class for every producer.
 *
 * The OTP capsule (CapsuleParser) still fills the older primaryText / secondaryText /
 * copyContent fields, so they stay; the getters below read either set.
 */
data class InspectPayload(
    val domain: String = "",
    val title: String = "",
    val subtitle: String = "",
    val fullContent: String = "",
    val copyText: String = "",
    // Older fields, still used by the OTP capsule.
    val primaryText: String = "",
    val secondaryText: String = "",
    val actionType: String = "COPY",
    val copyContent: String = "",
    val extraData: String = "",
    // Label/value rows, a link the card can open, and OK / WARN / ERROR.
    val rows: List<Pair<String, String>> = emptyList(),
    val url: String = "",
    val status: String = "OK",
    /** ARGB accent, 0 = use the domain default. */
    val accent: Int = 0,
    /** Colours drawn as a strip at the top of the card. */
    val palette: List<Int> = emptyList(),
    /** Site name the CHECK button looks up. */
    val checkHost: String = "",
    /** Shortened link the RESOLVE button expands. */
    val resolveUrl: String = "",
    val big: String = "",
    val imageUrl: String = "",
    val nums: List<Double> = emptyList(),
    val unit: String = "",
    val eventId: Long = 0L
) : Serializable {

    val bigText: String get() = big.ifBlank { heroText }
    val heroText: String get() = title.ifBlank { primaryText }
    val subText: String get() = subtitle.ifBlank { secondaryText }
    val copyValue: String get() = copyText.ifBlank { copyContent }.ifBlank { fullContent }

    /** A bare "tap to copy" payload (the OTP capsule): tapping copies instead of opening the overlay. */
    val isCopyOnTap: Boolean
        get() = actionType == "COPY" && copyContent.isNotBlank() &&
            copyText.isBlank() && fullContent.isBlank() && rows.isEmpty()

    companion object {
        private const val serialVersionUID = 5L
    }
}
