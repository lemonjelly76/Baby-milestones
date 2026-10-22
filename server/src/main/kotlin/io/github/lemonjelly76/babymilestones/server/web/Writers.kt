package io.github.lemonjelly76.babymilestones.server.web

import io.github.lemonjelly76.babymilestones.server.security.currentCarer
import io.github.lemonjelly76.babymilestones.server.service.Writer

/** The signed-in carer as the writer of a change made in the app. */
internal fun writer() = currentCarer().let { Writer(it.familyId, it.carerId) }
