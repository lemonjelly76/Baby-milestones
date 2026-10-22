package io.github.lemonjelly76.babymilestones.core.food

/** How food names are compared everywhere: case-insensitive, with a plural ending ignored ("eggs" is "egg"). */
object FoodNames {
    fun forms(text: String): List<String> {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return emptyList()
        return listOfNotNull(t, t.removeSuffix("es").takeIf { t.endsWith("es") }, t.removeSuffix("s").takeIf { t.endsWith("s") })
    }

    /** The family's own food is kept by name; tastings of a catalogue food share its id. */
    fun key(foodId: String?, name: String): String = foodId ?: ("own:" + name.trim().lowercase())
}
