package io.github.lemonjelly76.babymilestones.core.records

import io.github.lemonjelly76.babymilestones.core.json.Date
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A milk tooth in FDI notation (51 to 85) with the age it usually comes through. */
@Serializable
data class Tooth(val id: String, val name: String, val upper: Boolean, val typicalMonths: Int, val sources: List<String>)

@Serializable
data class ToothRecord(val babyId: String, val toothId: String, val cameOn: Date)

/** A word the child says, in the family's spelling, and when they first said it. */
@Serializable
data class FirstWord(val id: String, val babyId: String, val word: String, val saidOn: Date, val note: String? = null)

@Serializable
enum class ContactRole(val label: String) {
    @SerialName("health_visitor") HEALTH_VISITOR("Health visitor"),
    @SerialName("gp") GP("GP surgery"),
    @SerialName("midwife") MIDWIFE("Midwife"),
    @SerialName("nursery") NURSERY("Nursery or childminder"),
    @SerialName("dentist") DENTIST("Dentist"),
    @SerialName("other") OTHER("Other"),
}

/** Someone the family contacts about the children, like the red book's contacts page. */
@Serializable
data class Contact(
    val id: String,
    val role: ContactRole,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val note: String? = null,
)
