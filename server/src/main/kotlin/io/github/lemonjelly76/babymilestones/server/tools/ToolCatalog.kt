package io.github.lemonjelly76.babymilestones.server.tools

import org.springframework.stereotype.Component

/** Every tool from every [ToolSet], in a stable order. Each result carries the content version it came from. */
@Component
class ToolCatalog(sets: List<ToolSet>) {
    val tools: List<BabyTool> = sets.sortedBy { it.order }.flatMap { it.tools }

    init {
        val duplicates = tools.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
        check(duplicates.isEmpty()) { "Duplicate tool names: $duplicates" }
    }

    fun find(name: String): BabyTool? = tools.firstOrNull { it.name == name }
}
