package io.github.lemonjelly76.babymilestones.server.store

import java.sql.ResultSet
import java.time.LocalDate

/** Enums are stored as their lower-case names ("not_sure"), which match their JSON names. */
internal fun dbName(value: Enum<*>) = value.name.lowercase()

internal inline fun <reified E : Enum<E>> fromDb(value: String): E = enumValueOf(value.uppercase())

internal fun ResultSet.date(column: String): LocalDate? = getObject(column, LocalDate::class.java)
