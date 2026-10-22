package io.github.lemonjelly76.babymilestones.server.store

import io.github.lemonjelly76.babymilestones.core.food.FoodGroup
import io.github.lemonjelly76.babymilestones.core.food.FoodTasting
import io.github.lemonjelly76.babymilestones.core.food.Liking
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet

/** Each time a baby was offered a food. Callers check the baby belongs to the family first. */
@Repository
class FoodStore(private val jdbc: JdbcClient) {

    fun tastings(babyId: String): List<FoodTasting> =
        jdbc.sql("select * from food_tasting where baby_id = :babyId order by tasted_on, created_at").param("babyId", babyId)
            .query { rs, _ -> tasting(rs) }.list()

    fun recentTastings(babyId: String, limit: Int): List<FoodTasting> =
        jdbc.sql("select * from food_tasting where baby_id = :babyId order by created_at desc limit :limit")
            .param("babyId", babyId).param("limit", limit).query { rs, _ -> tasting(rs) }.list()

    fun tasting(babyId: String, id: String): FoodTasting? =
        jdbc.sql("select * from food_tasting where id = :id and baby_id = :babyId").param("id", id).param("babyId", babyId)
            .query { rs, _ -> tasting(rs) }.optional().orElse(null)

    fun saveTasting(t: FoodTasting) {
        jdbc.sql(
            """
            insert into food_tasting (id, baby_id, food_id, name, food_group, tasted_on, liking, reaction, note, photo_ref, logged_by)
            values (:id, :babyId, :foodId, :name, :group, :tastedOn, :liking, :reaction, :note, :photoRef, :loggedBy)
            on conflict (id) do update set tasted_on = excluded.tasted_on, liking = excluded.liking,
                reaction = excluded.reaction, note = excluded.note, photo_ref = excluded.photo_ref
            """,
        ).param("id", t.id).param("babyId", t.babyId).param("foodId", t.foodId).param("name", t.name)
            .param("group", dbName(t.group)).param("tastedOn", t.tastedOn).param("liking", t.liking?.let(::dbName))
            .param("reaction", t.reaction).param("note", t.note).param("photoRef", t.photoRef).param("loggedBy", t.loggedBy)
            .update()
    }

    fun deleteTasting(babyId: String, id: String): Boolean =
        jdbc.sql("delete from food_tasting where id = :id and baby_id = :babyId").param("id", id).param("babyId", babyId).update() > 0

    private fun tasting(rs: ResultSet) = FoodTasting(
        id = rs.getString("id"),
        babyId = rs.getString("baby_id"),
        foodId = rs.getString("food_id"),
        name = rs.getString("name"),
        group = fromDb<FoodGroup>(rs.getString("food_group")),
        tastedOn = rs.date("tasted_on")!!,
        liking = rs.getString("liking")?.let { fromDb<Liking>(it) },
        reaction = rs.getString("reaction"),
        note = rs.getString("note"),
        photoRef = rs.getString("photo_ref"),
        loggedBy = rs.getString("logged_by"),
    )
}
