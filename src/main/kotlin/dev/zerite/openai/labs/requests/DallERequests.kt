package dev.zerite.openai.labs.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Errors
 */
open class DallEError(override val message: String) : Exception()
class PromptRejectedError(message: String) : DallEError(message)

/**
 * Task post request
 */
@Serializable
data class TaskPostRequest(
    val prompt: Prompt,
    @SerialName("task_type") val taskType: String, // "text2im"
) {
    @Serializable
    data class Prompt(
        val caption: String,
        @SerialName("batch_size") val batchSize: Int = 4,
    )
}

/**
 * Image task response object
 */
@Serializable
data class ImageTask(
    val created: Long,
    val id: String,
    val `object`: String,
    val status: String, // "pending", "succeeded", "rejected"
    val generations: Generations? = null,
    @SerialName("status_information") val statusInformation: StatusInformation? = null,
) {
    @Serializable
    data class Generations(
        val data: List<Generation>
    )

    @Serializable
    data class Generation(
        val id: String,
        val generation: GenerationData
    ) {
        @Serializable
        data class GenerationData(
            @SerialName("image_path") val imagePath: String,
        )
    }

    @Serializable
    data class StatusInformation(
        val message: String? = null,
    )
}