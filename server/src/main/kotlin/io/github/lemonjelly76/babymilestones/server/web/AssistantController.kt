package io.github.lemonjelly76.babymilestones.server.web

import io.github.lemonjelly76.babymilestones.core.api.AssistantReply
import io.github.lemonjelly76.babymilestones.core.api.AssistantRequest
import io.github.lemonjelly76.babymilestones.core.api.ConfirmRequest
import io.github.lemonjelly76.babymilestones.core.api.ConfirmResult
import io.github.lemonjelly76.babymilestones.server.assistant.AssistantService
import io.github.lemonjelly76.babymilestones.server.security.currentCarer
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** The optional in-app assistant (off by default on the phone; it calls the Claude API). */
@RestController
@RequestMapping("/api")
class AssistantController(
    private val assistant: AssistantService,
) {
    @PostMapping("/assistant/messages")
    fun ask(@RequestBody request: AssistantRequest): AssistantReply = assistant.reply(currentCarer(), request.babyId, request.messages)

    @PostMapping("/assistant/confirm")
    fun confirm(@RequestBody request: ConfirmRequest): List<ConfirmResult> = assistant.confirm(currentCarer(), request.proposals)

}
