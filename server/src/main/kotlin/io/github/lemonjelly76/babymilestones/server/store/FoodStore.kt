package io.github.lemonjelly76.babymilestones.server.store

import io.github.lemonjelly76.babymilestones.core.food.FoodGroup
import io.github.lemonjelly76.babymilestones.core.food.FoodTasting
import io.github.lemonjelly76.babymilestones.core.food.Liking
import io.github.lemonjelly76.babymilestones.core.food.PlanKind
import io.github.lemonjelly76.babymilestones.core.food.PlanSource
import io.github.lemonjelly76.babymilestones.core.food.PlannedFood
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

    // --- the food plan ---

    fun plan(babyId: String): List<PlannedFood> =
        jdbc.sql("select * from food_plan_item where baby_id = :babyId order by planned_on, created_at").param("babyId", babyId)
            .query { rs, _ ->
                PlannedFood(
                    rs.getString("id"), rs.getString("baby_id"), rs.getString("food_id"), rs.getString("name"),
                    fromDb<FoodGroup>(rs.getString("food_group")), rs.date("planned_on")!!, fromDb<PlanKind>(rs.getString("kind")),
                    rs.getString("reason"), fromDb<PlanSource>(rs.getString("source")), rs.date("created_on")!!,
                )
            }.list()

    fun savePlanItem(item: PlannedFood, createdBy: String?) {
        jdbc.sql(
            """
            insert into food_plan_item (id, baby_id, food_id, name, food_group, planned_on, kind, reason, source, created_on, created_by)
            values (:id, :babyId, :foodId, :name, :group, :plannedOn, :kind, :reason, :source, :createdOn, :createdBy)
            """,
        ).param("id", item.id).param("babyId", item.babyId).param("foodId", item.foodId).param("name", item.name)
            .param("group", dbName(item.group)).param("plannedOn", item.plannedOn).param("kind", dbName(item.kind))
            .param("reason", item.reason).param("source", dbName(item.source)).param("createdOn", item.createdOn)
            .param("createdBy", createdBy).update()
    }

    fun deletePlan(babyId: String) {
        jdbc.sql("delete from food_plan_item where baby_id = :babyId").param("babyId", babyId).update()
    }

    fun deletePlanItem(babyId: String, id: String): Boolean =
        jdbc.sql("delete from food_plan_item where id = :id and baby_id = :babyId").param("id", id).param("babyId", babyId)
            .update() > 0
}
