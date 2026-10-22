package io.github.lemonjelly76.babymilestones.server.web

import io.github.lemonjelly76.babymilestones.server.service.ForbiddenException
import io.github.lemonjelly76.babymilestones.server.store.NotFoundException
import io.github.lemonjelly76.babymilestones.server.tools.ToolInputException
import kotlinx.serialization.SerializationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Errors come back as {"error": "..."} in plain words; no notes or milestone text are logged. */
@RestControllerAdvice
class ErrorHandling {

    @ExceptionHandler(NotFoundException::class)
    fun notFound(e: NotFoundException) = error(HttpStatus.NOT_FOUND, e.message)

    @ExceptionHandler(ForbiddenException::class)
    fun forbidden(e: ForbiddenException) = error(HttpStatus.FORBIDDEN, e.message)

    @ExceptionHandler(IllegalArgumentException::class, ToolInputException::class, SerializationException::class)
    fun badRequest(e: RuntimeException) = error(HttpStatus.BAD_REQUEST, e.message)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(e: HttpMessageNotReadableException) = error(HttpStatus.BAD_REQUEST, "The request couldn't be read")

    private fun error(status: HttpStatus, message: String?) =
        ResponseEntity.status(status).body(ErrorResponse(message ?: status.reasonPhrase))
}
