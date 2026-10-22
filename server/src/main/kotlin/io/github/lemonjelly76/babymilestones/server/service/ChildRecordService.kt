package io.github.lemonjelly76.babymilestones.server.service

import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.growth.GrowthMeasurement
import io.github.lemonjelly76.babymilestones.core.records.FirstWord
import io.github.lemonjelly76.babymilestones.core.records.ToothRecord
import io.github.lemonjelly76.babymilestones.server.store.BabyStore
import io.github.lemonjelly76.babymilestones.server.store.ChildRecordStore
import io.github.lemonjelly76.babymilestones.server.store.Ids
import io.github.lemonjelly76.babymilestones.server.store.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

data class GrowthInput(
    val measuredOn: LocalDate? = null,
    val weightKg: Double? = null,
    val lengthCm: Double? = null,
    val headCm: Double? = null,
    val measuredBy: String? = null,
    val note: String? = null,
)

/** Growth measurements, first words and teeth. Values outside these ranges are almost always typing slips. */
@Service
class ChildRecordService(
    private val records: ChildRecordStore,
    private val babies: BabyStore,
    private val access: BabyAccess,
    private val content: ContentBundle,
    private val clock: Clock,
) {
    companion object {
        val WEIGHT_KG = 0.3..40.0
        val LENGTH_CM = 25.0..130.0
        val HEAD_CM = 18.0..60.0
    }

    @Transactional
    fun logGrowth(writer: Writer, babyId: String, input: GrowthInput): GrowthMeasurement {
        val measuredOn = checkDate(writer, babyId, input.measuredOn)
        require(input.weightKg != null || input.lengthCm != null || input.headCm != null) { "Give a weight, length or head size" }
        input.weightKg?.let { require(it in WEIGHT_KG) { "Weight should be in kilograms, between 0.3 and 40" } }
        input.lengthCm?.let { require(it in LENGTH_CM) { "Length should be in centimetres, between 25 and 130" } }
        input.headCm?.let { require(it in HEAD_CM) { "Head size should be in centimetres, between 18 and 60" } }
        val m = GrowthMeasurement(
            Ids.new("g"), babyId, measuredOn, input.weightKg, input.lengthCm, input.headCm,
            input.measuredBy?.trim()?.ifEmpty { null }?.take(60), input.note?.trim()?.ifEmpty { null }?.take(500),
        )
        records.saveGrowth(m, writer.carerId)
        return m
    }

    @Transactional
    fun deleteGrowth(writer: Writer, babyId: String, id: String) {
        access.checkBaby(writer, babyId)
        if (!records.deleteGrowth(babyId, id)) throw NotFoundException("No measurement $id")
    }

    @Transactional
    fun logWord(writer: Writer, babyId: String, word: String, saidOn: LocalDate?, note: String?): FirstWord {
        val date = checkDate(writer, babyId, saidOn)
        val clean = word.trim().trim('"', '“', '”').take(60)
        require(clean.isNotEmpty()) { "Which word?" }
        require(!records.hasWord(babyId, clean)) { "“$clean” is already logged" }
        val w = FirstWord(Ids.new("w"), babyId, clean, date, note?.trim()?.ifEmpty { null }?.take(500))
        records.saveWord(w, writer.carerId)
        return w
    }

    @Transactional
    fun deleteWord(writer: Writer, babyId: String, id: String) {
        access.checkBaby(writer, babyId)
        if (!records.deleteWord(babyId, id)) throw NotFoundException("No word $id")
    }

    @Transactional
    fun logTooth(writer: Writer, babyId: String, toothId: String, cameOn: LocalDate?): ToothRecord {
        content.tooth(toothId) ?: throw NotFoundException("Unknown tooth $toothId")
        val record = ToothRecord(babyId, toothId, checkDate(writer, babyId, cameOn))
        records.saveTooth(record)
        return record
    }

    @Transactional
    fun deleteTooth(writer: Writer, babyId: String, toothId: String) {
        access.checkBaby(writer, babyId)
        if (!records.deleteTooth(babyId, toothId)) throw NotFoundException("No record of tooth $toothId")
    }

    /** Checks the baby and returns the date, defaulting to today; it can't be in the future or before birth. */
    private fun checkDate(writer: Writer, babyId: String, date: LocalDate?): LocalDate {
        access.checkBaby(writer, babyId)
        val today = LocalDate.now(clock)
        val day = date ?: today
        require(!day.isAfter(today)) { "The date can't be in the future" }
        val born = babies.baby(writer.familyId, babyId)!!.dateOfBirth
        require(!day.isBefore(born)) { "The date can't be before the date of birth" }
        return day
    }
}
