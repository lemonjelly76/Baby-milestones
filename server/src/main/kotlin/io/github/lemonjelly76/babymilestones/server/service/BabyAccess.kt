package io.github.lemonjelly76.babymilestones.server.service

import io.github.lemonjelly76.babymilestones.server.store.BabyStore
import io.github.lemonjelly76.babymilestones.server.store.FamilyStore
import io.github.lemonjelly76.babymilestones.server.store.NotFoundException
import org.springframework.stereotype.Component

/** The checks every write makes first: the baby, and any photo, belong to the writer's family. */
@Component
class BabyAccess(private val babies: BabyStore, private val families: FamilyStore) {

    fun checkBaby(writer: Writer, babyId: String) {
        babies.baby(writer.familyId, babyId) ?: throw NotFoundException("No baby $babyId in this family")
    }

    fun checkPhoto(writer: Writer, photoRef: String?) {
        if (photoRef != null && families.photo(writer.familyId, photoRef) == null) throw NotFoundException("No photo $photoRef")
    }
}
