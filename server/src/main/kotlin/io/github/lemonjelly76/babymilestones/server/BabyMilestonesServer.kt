package io.github.lemonjelly76.babymilestones.server

import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.content.ContentLoader
import io.github.lemonjelly76.babymilestones.core.guidance.GuidanceSearch
import io.github.lemonjelly76.babymilestones.core.json.BabyJson
import io.github.lemonjelly76.babymilestones.core.safety.SafetyCheck
import io.github.lemonjelly76.babymilestones.core.tracking.Tracker
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.http.converter.json.KotlinSerializationJsonHttpMessageConverter
import java.time.Clock
import java.time.ZoneId

@SpringBootApplication
class BabyMilestonesServer {

    /** The day boundary for "today" in age and status calculations. */
    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Europe/London"))

    @Bean
    fun content(): ContentBundle = ContentLoader.fromClasspath()

    @Bean
    fun tracker(content: ContentBundle) = Tracker(content)

    @Bean
    fun guidanceSearch(content: ContentBundle) = GuidanceSearch(content)

    @Bean
    fun safetyCheck(content: ContentBundle) = SafetyCheck(content.safety)

    /** The app's API speaks the same snake_case JSON as the content bundle and the MCP tools. */
    @Bean
    fun kotlinSerializationConverter() = KotlinSerializationJsonHttpMessageConverter(BabyJson)
}

fun main(args: Array<String>) {
    runApplication<BabyMilestonesServer>(*args)
}
