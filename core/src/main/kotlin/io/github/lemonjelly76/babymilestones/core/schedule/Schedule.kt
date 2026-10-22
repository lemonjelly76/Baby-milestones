package io.github.lemonjelly76.babymilestones.core.schedule

import io.github.lemonjelly76.babymilestones.core.content.AppointmentRule
import io.github.lemonjelly76.babymilestones.core.content.DoseRule
import io.github.lemonjelly76.babymilestones.core.content.RegionSchedule
import io.github.lemonjelly76.babymilestones.core.json.Date
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** What a carer has recorded about an appointment; the window itself always comes from the schedule. */
@Serializable
data class AppointmentRecord(
    val id: String,
    val babyId: String,
    val type: String,
    val bookedOn: Date? = null,
    val doneOn: Date? = null,
    val note: String? = null,
)

@Serializable
data class DoseRecord(val id: String, val babyId: String, val doseCode: String, val givenOn: Date, val note: String? = null)

@Serializable
enum class AppointmentStatus {
    @SerialName("upcoming") UPCOMING,
    @SerialName("open") OPEN,
    @SerialName("booked") BOOKED,
    @SerialName("done") DONE,
    @SerialName("past_window") PAST_WINDOW,
}

@Serializable
enum class DoseStatus {
    @SerialName("upcoming") UPCOMING,
    @SerialName("due") DUE,
    @SerialName("given") GIVEN,
    @SerialName("overdue") OVERDUE,
}

@Serializable
data class ScheduledAppointment(
    val rule: AppointmentRule,
    val windowStart: Date,
    val windowEnd: Date,
    val status: AppointmentStatus,
    val record: AppointmentRecord? = null,
)

@Serializable
data class ScheduledDose(val rule: DoseRule, val dueOn: Date, val status: DoseStatus, val record: DoseRecord? = null)

@Serializable
data class BabySchedule(
    val region: String,
    val version: String,
    val appointments: List<ScheduledAppointment>,
    val doses: List<ScheduledDose>,
) {
    fun nextAppointment(): ScheduledAppointment? = appointments
        .filter { it.status in setOf(AppointmentStatus.UPCOMING, AppointmentStatus.OPEN, AppointmentStatus.BOOKED) }
        .minByOrNull { it.record?.bookedOn ?: it.windowStart }

    /** Windows that have closed without being marked booked or done. */
    fun appointmentsToMark() = appointments.filter { it.status == AppointmentStatus.PAST_WINDOW }
    fun dosesDue() = doses.filter { it.status == DoseStatus.DUE || it.status == DoseStatus.OVERDUE }
}

/** Appointments and vaccinations always run on chronological age, from the schedule version for this date of birth. */
object ScheduleEngine {
    /** A dose counts as overdue this long after it was due. */
    const val DOSE_OVERDUE_DAYS = 28L

    fun forBaby(
        dateOfBirth: LocalDate,
        schedule: RegionSchedule,
        appointmentRecords: List<AppointmentRecord>,
        doseRecords: List<DoseRecord>,
        today: LocalDate,
    ): BabySchedule {
        val appointments = schedule.appointmentsFor(dateOfBirth).map { rule ->
            val start = rule.windowStart.from(dateOfBirth)
            val end = rule.windowEnd.from(dateOfBirth)
            val record = appointmentRecords.firstOrNull { it.type == rule.id }
            val status = when {
                record?.doneOn != null -> AppointmentStatus.DONE
                record?.bookedOn != null -> AppointmentStatus.BOOKED
                today.isBefore(start) -> AppointmentStatus.UPCOMING
                today.isAfter(end) -> AppointmentStatus.PAST_WINDOW
                else -> AppointmentStatus.OPEN
            }
            ScheduledAppointment(rule, start, end, status, record)
        }.sortedBy { it.windowStart }

        val doses = schedule.dosesFor(dateOfBirth).map { rule ->
            val due = rule.age.from(dateOfBirth)
            val record = doseRecords.firstOrNull { it.doseCode == rule.code }
            val status = when {
                record != null -> DoseStatus.GIVEN
                today.isBefore(due) -> DoseStatus.UPCOMING
                today.isAfter(due.plusDays(DOSE_OVERDUE_DAYS)) -> DoseStatus.OVERDUE
                else -> DoseStatus.DUE
            }
            ScheduledDose(rule, due, status, record)
        }.sortedBy { it.dueOn }

        return BabySchedule(schedule.region, schedule.version, appointments, doses)
    }
}
