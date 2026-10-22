package io.github.lemonjelly76.babymilestones.server.web

import io.github.lemonjelly76.babymilestones.core.api.GrowthRequest
import io.github.lemonjelly76.babymilestones.core.api.ToothRequest
import io.github.lemonjelly76.babymilestones.core.api.WordRequest
import io.github.lemonjelly76.babymilestones.core.growth.GrowthMeasurement
import io.github.lemonjelly76.babymilestones.core.records.FirstWord
import io.github.lemonjelly76.babymilestones.core.records.ToothRecord
import io.github.lemonjelly76.babymilestones.server.service.ChildRecordService
import io.github.lemonjelly76.babymilestones.server.service.GrowthInput
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Growth measurements, first words and teeth. */
@RestController
@RequestMapping("/api")
class RecordController(
    private val childRecords: ChildRecordService,
) {
    @PostMapping("/babies/{babyId}/growth")
    @ResponseStatus(HttpStatus.CREATED)
    fun logGrowth(@PathVariable babyId: String, @RequestBody request: GrowthRequest): GrowthMeasurement =
        childRecords.logGrowth(writer(), babyId, GrowthInput(
            request.measuredOn, request.weightKg, request.lengthCm, request.headCm, request.measuredBy, request.note,
        ))

    @DeleteMapping("/babies/{babyId}/growth/{measurementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteGrowth(@PathVariable babyId: String, @PathVariable measurementId: String) =
        childRecords.deleteGrowth(writer(), babyId, measurementId)

    @PostMapping("/babies/{babyId}/words")
    @ResponseStatus(HttpStatus.CREATED)
    fun logWord(@PathVariable babyId: String, @RequestBody request: WordRequest): FirstWord =
        childRecords.logWord(writer(), babyId, request.word, request.saidOn, request.note)

    @DeleteMapping("/babies/{babyId}/words/{wordId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteWord(@PathVariable babyId: String, @PathVariable wordId: String) = childRecords.deleteWord(writer(), babyId, wordId)

    @PutMapping("/babies/{babyId}/teeth/{toothId}")
    fun logTooth(@PathVariable babyId: String, @PathVariable toothId: String, @RequestBody request: ToothRequest): ToothRecord =
        childRecords.logTooth(writer(), babyId, toothId, request.cameOn)

    @DeleteMapping("/babies/{babyId}/teeth/{toothId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteTooth(@PathVariable babyId: String, @PathVariable toothId: String) = childRecords.deleteTooth(writer(), babyId, toothId)
}
