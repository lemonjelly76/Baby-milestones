package io.github.lemonjelly76.babymilestones.core.content

import io.github.lemonjelly76.babymilestones.core.food.Food
import io.github.lemonjelly76.babymilestones.core.food.FoodImage
import io.github.lemonjelly76.babymilestones.core.food.FoodNames
import io.github.lemonjelly76.babymilestones.core.growth.GrowthReference
import io.github.lemonjelly76.babymilestones.core.growth.Measure
import io.github.lemonjelly76.babymilestones.core.guides.Guide
import io.github.lemonjelly76.babymilestones.core.records.Tooth
import io.github.lemonjelly76.babymilestones.core.json.BabyJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The content bundle ships as separate JSON files under `content/` on the classpath, so each can be reviewed on its own.
 * The server serves the assembled bundle; the app caches the newest one it has seen.
 */
object ContentLoader {

    @Serializable
    private data class Manifest(val version: String, val locale: String)

    fun fromClasspath(json: Json = BabyJson): ContentBundle {
        fun read(name: String): String =
            ContentLoader::class.java.getResourceAsStream("/content/$name")?.bufferedReader()?.use { it.readText() }
                ?: error("Missing content file $name")

        val manifest = json.decodeFromString<Manifest>(read("manifest.json"))
        return ContentBundle(
            version = manifest.version,
            locale = manifest.locale,
            references = json.decodeFromString(ListSerializer(Reference.serializer()), read("references.json")),
            periods = json.decodeFromString(ListSerializer(PeriodGuide.serializer()), read("periods.json")),
            milestones = json.decodeFromString(ListSerializer(Milestone.serializer()), read("milestones.json")),
            concerns = json.decodeFromString(read("concerns.json")),
            safety = json.decodeFromString(read("safety.json")),
            schedules = json.decodeFromString(ListSerializer(RegionSchedule.serializer()), read("schedules.json")),
            foods = json.decodeFromString(ListSerializer(Food.serializer()), read("foods.json")),
            guides = json.decodeFromString(ListSerializer(Guide.serializer()), read("guides.json")),
            growth = json.decodeFromString(GrowthReference.serializer(), read("growth.json")),
            teeth = json.decodeFromString(ListSerializer(Tooth.serializer()), read("teeth.json")),
        ).also { bundle ->
            val problems = ContentValidator.problems(bundle)
            require(problems.isEmpty()) { "Content bundle ${bundle.version} is invalid:\n" + problems.joinToString("\n") }
        }
    }

    fun fromJson(text: String, json: Json = BabyJson): ContentBundle = json.decodeFromString(text)
}

object ContentValidator {

    fun problems(bundle: ContentBundle): List<String> = buildList {
        val milestoneIds = bundle.milestones.map { it.id }
        milestoneIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            .forEach { add("Duplicate milestone id $it") }
        val referenceIds = bundle.references.map { it.id }.toSet()
        val known = milestoneIds.toSet()

        fun checkSources(owner: String, sources: List<String>) {
            if (sources.isEmpty()) add("$owner cites no source")
            sources.filterNot { it in referenceIds }.forEach { add("$owner cites unknown reference $it") }
        }

        bundle.milestones.forEach { m ->
            val prefix = when (m.domain) {
                Domain.SOCIAL -> "social."
                Domain.LANGUAGE -> "lang."
                Domain.COGNITIVE -> "cog."
                Domain.MOVEMENT -> "move."
            }
            if (!m.id.startsWith(prefix)) add("${m.id} should start with $prefix")
            if (m.emergingFrom < 0 || m.emergingFrom >= m.expectedBy) add("${m.id} must emerge before it is expected")
            (m.leadsTo + m.buildsOn).filterNot { it in known }.forEach { add("${m.id} links to unknown milestone $it") }
            m.leadsTo.mapNotNull { bundle.milestone(it) }.filter { it.expectedBy < m.expectedBy }
                .forEach { add("${m.id} leads to ${it.id}, which is expected earlier") }
            m.typicalWindow?.let { if (it.reference !in referenceIds) add("${m.id} window cites unknown ${it.reference}") }
            checkSources(m.id, m.sources)
        }

        bundle.periods.zipWithNext().forEach { (a, b) ->
            if (a.toMonths != b.fromMonths) add("Gap between periods ${a.id} and ${b.id}")
        }
        bundle.periods.forEach { checkSources(it.id, it.sources) }
        checkSources("concerns", bundle.concerns.sources)
        checkFoods(bundle, ::checkSources)
        checkGuides(bundle, ::checkSources)
        bundle.growth?.let { g ->
            checkSources("growth", g.sources)
            Measure.entries.forEach { m ->
                listOf("female", "male").forEach { sex ->
                    val t = g.table(m, sex)
                    if (t == null) add("No growth table for $m $sex")
                    else if (t.l.size != t.m.size || t.m.size != t.s.size || t.m.size < 2) add("Growth table $m $sex is incomplete")
                }
            }
        }
        if (bundle.teeth.map { it.id }.toSet().size != bundle.teeth.size) add("Duplicate tooth id")
        bundle.teeth.forEach { checkSources("tooth ${it.id}", it.sources) }
        bundle.schedules.forEach { s ->
            val appointmentIds = s.appointments.map { it.id }.toSet()
            s.appointments.forEach { checkSources("${s.region}/${it.id}", it.sources) }
            s.doses.forEach { d ->
                checkSources("${s.region}/${d.code}", d.sources)
                if (d.appointment !in appointmentIds) add("${d.code} names unknown appointment ${d.appointment}")
            }
        }
    }
}

private fun MutableList<String>.checkFoods(bundle: ContentBundle, checkSources: (String, List<String>) -> Unit) {
    bundle.foods.map { it.id }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { add("Duplicate food id $it") }
    bundle.foods.map { it.name.lowercase() }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        .forEach { add("Duplicate food name $it") }
    bundle.foods.flatMap { f -> (listOf(f.name) + f.aliases).flatMap(FoodNames::forms).map { it to f.id }.distinct() }
        .groupBy({ it.first }, { it.second }).filterValues { it.size > 1 }
        .forEach { (name, ids) -> add("'$name' names more than one food: ${ids.joinToString()}") }
    bundle.foods.forEach { f ->
        if (!f.id.startsWith("food.")) add("${f.id} should start with food.")
        if (f.serving.isEmpty()) add("${f.id} has no serving advice")
        f.image?.let { if (!FoodImage.OPEN.containsMatchIn(it.licence)) add("${f.id} photo licence ${it.licence} isn't open") }
        if (f.serving.zipWithNext().any { (a, b) -> a.fromMonths >= b.fromMonths }) add("${f.id} serving advice is out of age order")
        f.serving.firstOrNull()?.let { if (it.fromMonths > f.fromMonths) add("${f.id} has no serving advice from ${f.fromMonths} months") }
        checkSources(f.id, f.sources)
    }
}

private fun MutableList<String>.checkGuides(bundle: ContentBundle, checkSources: (String, List<String>) -> Unit) {
    bundle.guides.map { it.id }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { add("Duplicate guide id $it") }
    val itemIds = bundle.guides.flatMap { g -> g.checklist.map { it.id } }
    itemIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { add("Duplicate checklist item $it") }
    bundle.guides.forEach { g ->
        if (!g.id.startsWith("guide.")) add("${g.id} should start with guide.")
        if (g.fromMonths < 0 || g.fromMonths >= g.toMonths) add("${g.id} must start before it ends")
        if (g.sections.isEmpty()) add("${g.id} has no text")
        g.checklist.filterNot { it.id.startsWith("check.") }.forEach { add("${it.id} should start with check.") }
        g.links.filterNot { it.url.startsWith("https://") }.forEach { add("${g.id} link ${it.url} isn't https") }
        g.related.filter { bundle.guide(it) == null }.forEach { add("${g.id} relates to unknown guide $it") }
        checkSources(g.id, g.sources)
    }
}

/** Content versions look like 2026.10.1 and compare part by part as numbers. */
object ContentVersions {
    fun newer(candidate: String, than: String): Boolean {
        val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
        val b = than.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (diff != 0) return diff > 0
        }
        return false
    }
}
