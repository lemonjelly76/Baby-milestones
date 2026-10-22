package io.github.lemonjelly76.babymilestones.server.store

import io.github.lemonjelly76.babymilestones.core.growth.GrowthMeasurement
import io.github.lemonjelly76.babymilestones.core.records.FirstWord
import io.github.lemonjelly76.babymilestones.core.records.ToothRecord
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDate

/** Growth measurements, first words and teeth. Callers check the baby belongs to the family first. */
@Repository
class ChildRecordStore(private val jdbc: JdbcClient) {

    fun growth(babyId: String): List<GrowthMeasurement> =
        jdbc.sql("select * from growth_measurement where baby_id = :babyId order by measured_on, created_at")
            .param("babyId", babyId).query { rs, _ -> measurement(rs) }.list()

    fun saveGrowth(m: GrowthMeasurement, createdBy: String?) {
        jdbc.sql(
            """
            insert into growth_measurement (id, baby_id, measured_on, weight_kg, length_cm, head_cm, measured_by, note, created_by)
            values (:id, :babyId, :measuredOn, :weight, :length, :head, :measuredBy, :note, :createdBy)
            """,
        ).param("id", m.id).param("babyId", m.babyId).param("measuredOn", m.measuredOn).param("weight", m.weightKg)
            .param("length", m.lengthCm).param("head", m.headCm).param("measuredBy", m.measuredBy).param("note", m.note)
            .param("createdBy", createdBy).update()
    }

    fun deleteGrowth(babyId: String, id: String): Boolean =
        jdbc.sql("delete from growth_measurement where id = :id and baby_id = :babyId").param("id", id).param("babyId", babyId)
            .update() > 0

    fun words(babyId: String): List<FirstWord> =
        jdbc.sql("select * from first_word where baby_id = :babyId order by said_on, created_at").param("babyId", babyId)
            .query { rs, _ -> FirstWord(rs.getString("id"), rs.getString("baby_id"), rs.getString("word"), date(rs, "said_on"), rs.getString("note")) }
            .list()

    fun hasWord(babyId: String, word: String): Boolean =
        jdbc.sql("select count(*) from first_word where baby_id = :babyId and lower(word) = lower(:word)")
            .param("babyId", babyId).param("word", word).query(Int::class.java).single() > 0

    fun saveWord(w: FirstWord, createdBy: String?) {
        jdbc.sql("insert into first_word (id, baby_id, word, said_on, note, created_by) values (:id, :babyId, :word, :saidOn, :note, :createdBy)")
            .param("id", w.id).param("babyId", w.babyId).param("word", w.word).param("saidOn", w.saidOn).param("note", w.note)
            .param("createdBy", createdBy).update()
    }

    fun deleteWord(babyId: String, id: String): Boolean =
        jdbc.sql("delete from first_word where id = :id and baby_id = :babyId").param("id", id).param("babyId", babyId).update() > 0

    fun teeth(babyId: String): List<ToothRecord> =
        jdbc.sql("select * from tooth where baby_id = :babyId order by came_on").param("babyId", babyId)
            .query { rs, _ -> ToothRecord(rs.getString("baby_id"), rs.getString("tooth_id"), date(rs, "came_on")) }.list()

    fun saveTooth(t: ToothRecord) {
        jdbc.sql(
            """
            insert into tooth (baby_id, tooth_id, came_on) values (:babyId, :toothId, :cameOn)
            on conflict (baby_id, tooth_id) do update set came_on = excluded.came_on
            """,
        ).param("babyId", t.babyId).param("toothId", t.toothId).param("cameOn", t.cameOn).update()
    }

    fun deleteTooth(babyId: String, toothId: String): Boolean =
        jdbc.sql("delete from tooth where baby_id = :babyId and tooth_id = :toothId").param("babyId", babyId)
            .param("toothId", toothId).update() > 0

    private fun measurement(rs: ResultSet) = GrowthMeasurement(
        id = rs.getString("id"),
        babyId = rs.getString("baby_id"),
        measuredOn = date(rs, "measured_on"),
        weightKg = rs.getObject("weight_kg") as Double?,
        lengthCm = rs.getObject("length_cm") as Double?,
        headCm = rs.getObject("head_cm") as Double?,
        measuredBy = rs.getString("measured_by"),
        note = rs.getString("note"),
    )

    private fun date(rs: ResultSet, column: String): LocalDate = rs.getObject(column, LocalDate::class.java)
}
