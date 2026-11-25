package de.steppicrew.kladde.adx

import de.steppicrew.adx.AdxAttr
import de.steppicrew.adx.AdxStreamProposal
import de.steppicrew.adx.AdxSub
import de.steppicrew.kladde.model.SubTypeLabel
import de.steppicrew.kladde.model.SubTypeLabelStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class LabelFormTest {
    private val drink = listOf(VOLUME_ML, ABV_PCT)
    private val water = listOf(VOLUME_ML)
    private val meal = listOf("food.meal", "food.protein_g", "beercounter.glass")

    @Test
    fun `a drink stream offers alcoholic, non-alcoholic or count only`() {
        assertEquals(listOf(LabelKind.ALCOHOLIC, LabelKind.NON_ALCOHOLIC, LabelKind.COUNT_ONLY), labelKinds(drink))
    }

    @Test
    fun `a stream with other numbers offers values or count only`() {
        assertEquals(listOf(LabelKind.VALUES, LabelKind.COUNT_ONLY), labelKinds(water))
        assertEquals(listOf(LabelKind.VALUES, LabelKind.COUNT_ONLY), labelKinds(meal))
    }

    @Test
    fun `a stream without numbers only counts`() {
        assertEquals(listOf(LabelKind.COUNT_ONLY), labelKinds(emptyList()))
        assertEquals(listOf(LabelKind.COUNT_ONLY), labelKinds(listOf("food.meal")))
    }

    @Test
    fun `only registry numbers are asked for`() {
        assertEquals(listOf("food.protein_g"), labelFields(LabelKind.VALUES, meal))
    }

    @Test
    fun `an alcoholic drink stores volume and strength`() {
        assertEquals(
            mapOf(VOLUME_ML to 500.0, ABV_PCT to 4.9),
            labelDefaults(LabelKind.ALCOHOLIC, drink, mapOf(VOLUME_ML to "500", ABV_PCT to "4,9")),
        )
    }

    @Test
    fun `a non-alcoholic drink stores 0 percent whatever was typed for it`() {
        assertEquals(listOf(VOLUME_ML), labelFields(LabelKind.NON_ALCOHOLIC, drink))
        assertEquals(
            mapOf(VOLUME_ML to 330.0, ABV_PCT to 0.0),
            labelDefaults(LabelKind.NON_ALCOHOLIC, drink, mapOf(VOLUME_ML to "330", ABV_PCT to "5")),
        )
    }

    @Test
    fun `count only stores nothing`() {
        assertEquals(emptyMap<String, Double>(), labelDefaults(LabelKind.COUNT_ONLY, drink, mapOf(VOLUME_ML to "500")))
    }

    @Test
    fun `a blank field stores nothing for it`() {
        assertEquals(mapOf(ABV_PCT to 12.0), labelDefaults(LabelKind.ALCOHOLIC, drink, mapOf(VOLUME_ML to " ", ABV_PCT to "12")))
    }

    @Test
    fun `a value that isn't a number ≥ 0 stores nothing at all`() {
        assertNull(labelDefaults(LabelKind.ALCOHOLIC, drink, mapOf(VOLUME_ML to "half a litre")))
        assertNull(labelDefaults(LabelKind.ALCOHOLIC, drink, mapOf(VOLUME_ML to "-1")))
        assertNull(parseValue("NaN"))
    }

    @Test
    fun `values are shown without a trailing zero and parse back`() {
        assertEquals("330", formatValue(330.0, java.util.Locale.GERMANY))
        assertEquals("4,9", formatValue(4.9, java.util.Locale.GERMANY))
        assertEquals("4.9", formatValue(4.9, java.util.Locale.US))
        assertEquals(4.9, parseValue(formatValue(4.9, java.util.Locale.GERMANY)))
    }

    private val open = SubTypeLabel("l1", "t1", "beerSmall", SubTypeLabelStatus.OPEN, Instant.EPOCH)

    private fun stream(
        sub: AdxSub,
        attrs: List<String> = drink,
    ) = AdxStreamProposal("drinks.beer", "Beer", "counter", "count", listOf(sub), attrs.map(::AdxAttr))

    @Test
    fun `a named sub with every asked value but grams of alcohol is described`() {
        val label = open.describedBy(stream(AdxSub("beerSmall", "Small beer", mapOf(VOLUME_ML to 330, ABV_PCT to 4.9))))

        assertEquals(SubTypeLabelStatus.DESCRIBED, label.status)
        assertEquals(mapOf(VOLUME_ML to 330.0, ABV_PCT to 4.9), label.senderDefaults)
        assertEquals(drink, label.attrKeys)
    }

    @Test
    fun `a sub without a name or without a value stays open`() {
        assertEquals(
            SubTypeLabelStatus.OPEN,
            open.describedBy(stream(AdxSub("beerSmall", null, mapOf(VOLUME_ML to 330, ABV_PCT to 5)))).status,
        )
        assertEquals(SubTypeLabelStatus.OPEN, open.describedBy(stream(AdxSub("beerSmall", "Small beer", mapOf(VOLUME_ML to 330)))).status)
    }

    @Test
    fun `a named sub of a stream without asked values is described`() {
        val radler = open.copy(key = "Radler")

        assertEquals(SubTypeLabelStatus.DESCRIBED, radler.describedBy(stream(AdxSub("Radler", "Radler"), emptyList())).status)
    }

    @Test
    fun `values that aren't numbers are no defaults`() {
        val label = open.describedBy(stream(AdxSub("beerSmall", "Small beer", mapOf(VOLUME_ML to "half", "beercounter.lactose" to true))))

        assertEquals(emptyMap<String, Double>(), label.senderDefaults)
    }

    @Test
    fun `a label the user answered keeps its status`() {
        val ignored = open.copy(status = SubTypeLabelStatus.IGNORED)

        assertEquals(
            SubTypeLabelStatus.IGNORED,
            ignored
                .describedBy(
                    stream(
                        AdxSub(
                            "beerSmall",
                            "Small beer",
                            mapOf(
                                VOLUME_ML to 330,
                                ABV_PCT to 5,
                            ),
                        ),
                    ),
                ).status,
        )
    }
}
