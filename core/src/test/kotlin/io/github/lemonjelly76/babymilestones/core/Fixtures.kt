package io.github.lemonjelly76.babymilestones.core

import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.content.ContentLoader
import io.github.lemonjelly76.babymilestones.core.tracking.Baby
import io.github.lemonjelly76.babymilestones.core.tracking.LogSource
import io.github.lemonjelly76.babymilestones.core.tracking.LogState
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneLog
import java.time.LocalDate

object Fixtures {
    val content: ContentBundle by lazy { ContentLoader.fromClasspath() }

    fun date(s: String): LocalDate = LocalDate.parse(s)

    fun baby(dob: String, due: String? = null, id: String = "b_01", name: String = "Ava") =
        Baby(id, name, date(dob), due?.let(::date))

    fun achieved(milestoneId: String, on: String, babyId: String = "b_01", lostOn: String? = null) = MilestoneLog(
        id = "log_$milestoneId", babyId = babyId, milestoneId = milestoneId, state = LogState.ACHIEVED,
        achievedOn = date(on), markedOn = date(on), source = LogSource.MANUAL, lostOn = lostOn?.let(::date),
    )

    fun notSure(milestoneId: String, markedOn: String, babyId: String = "b_01") = MilestoneLog(
        id = "log_$milestoneId", babyId = babyId, milestoneId = milestoneId, state = LogState.NOT_SURE,
        markedOn = date(markedOn),
    )
}
