package de.steppicrew.kladde.adx

internal const val VOLUME_ML = "intake.volume_ml"
internal const val ABV_PCT = "intake.abv_pct"
internal const val ALCOHOL_G = "intake.alcohol_g"

/**
 * The ADX registry attrs the label screen asks values for, in this order. Others, such as
 * `food.meal` (not a number) or an app's private keys, it leaves alone.
 */
internal val ASKED_ATTRS =
    listOf(
        VOLUME_ML,
        ABV_PCT,
        ALCOHOL_G,
        "intake.kcal",
        "intake.caffeine_mg",
        "food.protein_g",
        "food.carbs_g",
        "food.fat_g",
        "food.fibre_g",
    )

/** What a label is, as the label screen offers it. */
internal enum class LabelKind {
    ALCOHOLIC,
    NON_ALCOHOLIC,
    VALUES,
    COUNT_ONLY,
}

/** The kinds to offer for a label whose stream announced [attrKeys]; the first is preselected. */
internal fun labelKinds(attrKeys: List<String>): List<LabelKind> {
    val asked = askedAttrs(attrKeys)
    return when {
        ABV_PCT in asked -> listOf(LabelKind.ALCOHOLIC, LabelKind.NON_ALCOHOLIC, LabelKind.COUNT_ONLY)
        asked.isNotEmpty() -> listOf(LabelKind.VALUES, LabelKind.COUNT_ONLY)
        else -> listOf(LabelKind.COUNT_ONLY)
    }
}

/** The attrs to ask values for when the label is of [kind]. */
internal fun labelFields(
    kind: LabelKind,
    attrKeys: List<String>,
): List<String> =
    when (kind) {
        LabelKind.COUNT_ONLY -> emptyList()
        // Without alcohol, strength and grams of it are known: none.
        LabelKind.NON_ALCOHOLIC -> askedAttrs(attrKeys) - ABV_PCT - ALCOHOL_G
        LabelKind.ALCOHOLIC, LabelKind.VALUES -> askedAttrs(attrKeys)
    }

/** A value as typed, with a decimal comma or point; null when it isn't a number ≥ 0. */
internal fun parseValue(text: String): Double? =
    text
        .trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= 0 }

/**
 * The default values to store for a label of [kind], from the [typed] text per attr key.
 * A blank field stores nothing; null while any typed value isn't a number.
 */
internal fun labelDefaults(
    kind: LabelKind,
    attrKeys: List<String>,
    typed: Map<String, String>,
): Map<String, Double>? {
    val values = LinkedHashMap<String, Double>()
    for (key in labelFields(kind, attrKeys)) {
        val text = typed[key].orEmpty()
        if (text.isBlank()) continue
        values[key] = parseValue(text) ?: return null
    }
    if (kind == LabelKind.NON_ALCOHOLIC && ABV_PCT in attrKeys) values[ABV_PCT] = 0.0
    return values
}

private fun askedAttrs(attrKeys: List<String>): List<String> = ASKED_ATTRS.filter { it in attrKeys }
