package io.github.lemonjelly76.babymilestones.server.web

import io.github.lemonjelly76.babymilestones.core.api.ChecklistRequest
import io.github.lemonjelly76.babymilestones.core.api.ChecklistResponse
import io.github.lemonjelly76.babymilestones.core.api.TastingRequest
import io.github.lemonjelly76.babymilestones.core.api.TastingUpdateRequest
import io.github.lemonjelly76.babymilestones.core.api.GeneratePlanRequest
import io.github.lemonjelly76.babymilestones.core.food.FoodTasting
import io.github.lemonjelly76.babymilestones.core.food.PlannedFood
import io.github.lemonjelly76.babymilestones.server.service.FoodService
import io.github.lemonjelly76.babymilestones.server.service.GuideService
import io.github.lemonjelly76.babymilestones.server.service.TastingChanges
import io.github.lemonjelly76.babymilestones.server.service.TastingInput
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** The foods tracker, guide checklists and the notes Claude saves. */
@RestController
@RequestMapping("/api")
class FoodController(
    private val foods: FoodService,
    private val guides: GuideService,
) {
    @PostMapping("/babies/{babyId}/tastings")
    @ResponseStatus(HttpStatus.CREATED)
    fun logFood(@PathVariable babyId: String, @RequestBody request: TastingRequest): FoodTasting =
        foods.log(writer(), babyId, TastingInput(
            request.foodId, request.name, request.group, request.tastedOn, request.liking, request.reaction, request.note,
            request.photoRef,
        ))

    @PatchMapping("/babies/{babyId}/tastings/{tastingId}")
    fun updateFood(@PathVariable babyId: String, @PathVariable tastingId: String, @RequestBody request: TastingUpdateRequest): FoodTasting =
        foods.update(writer(), babyId, tastingId, TastingChanges(
            request.tastedOn, request.liking, request.reaction, request.note, request.photoRef, request.clearLiking, request.clearPhoto,
        ))

    @DeleteMapping("/babies/{babyId}/tastings/{tastingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeFood(@PathVariable babyId: String, @PathVariable tastingId: String) = foods.remove(writer(), babyId, tastingId)

    @PutMapping("/babies/{babyId}/checklist/{itemId}")
    fun checklist(@PathVariable babyId: String, @PathVariable itemId: String, @RequestBody request: ChecklistRequest): ChecklistResponse =
        ChecklistResponse(itemId, request.done, guides.setChecklistItem(writer(), babyId, itemId, request.done))

    @DeleteMapping("/babies/{babyId}/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAssistantNote(@PathVariable babyId: String, @PathVariable noteId: String) = guides.deleteNote(writer(), babyId, noteId)

    @PostMapping("/babies/{babyId}/food-plan/generate")
    fun generatePlan(@PathVariable babyId: String, @RequestBody request: GeneratePlanRequest): List<PlannedFood> =
        foods.generatePlan(writer(), babyId, request.days)

    @DeleteMapping("/babies/{babyId}/food-plan")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun clearPlan(@PathVariable babyId: String) = foods.clearPlan(writer(), babyId)

    @DeleteMapping("/babies/{babyId}/food-plan/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removePlanItem(@PathVariable babyId: String, @PathVariable itemId: String) = foods.removePlanItem(writer(), babyId, itemId)
}
