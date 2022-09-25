package dev.zerite.openai.labs

import dev.zerite.openai.labs.requests.ImageTask
import dev.zerite.openai.labs.requests.LabsLoginResponse
import dev.zerite.openai.labs.requests.PromptRejectedError
import dev.zerite.openai.labs.requests.TaskPostRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

class OpenAILabsClient(private val auth: LabsLoginResponse) {

    companion object {
        private const val BASE_URL = "https://labs.openai.com/api"
    }

    private val client = HttpClient(OkHttp) {
        install(DefaultRequest) {
            header("Authorization", "Bearer ${auth.user.session.sensitiveId}")
            header("User-Agent", "OpenAI Labs Kotlin Client")
        }

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    /**
     * Create images from text prompts.
     *
     * @param  prompt  The prompt to use for the image.
     * @param amount The amount of images to generate.
     * @return A list of images.
     * @throws PromptRejectedError If the prompt is rejected.
     */
    suspend fun createImages(prompt: String, amount: Int = 4): List<ImageTask.Generation> {
        // Create the request
        val request = TaskPostRequest(TaskPostRequest.Prompt(prompt, batchSize = amount), "text2im")
        val tasksRes = client.post("$BASE_URL/labs/tasks") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        // Store the body
        var task: ImageTask = tasksRes.body()

        // Poll for the task to be completed
        while (task.status != "succeeded") {
            val res = client.get("$BASE_URL/labs/tasks/${task.id}")
            task = res.body()

            // Check if the task has failed
            if (task.status == "rejected") {
                throw PromptRejectedError(task.statusInformation?.message ?: "Unknown error")
            }

            // Wait 1 second
            delay(1000)
        }

        // Return the generations
        return task.generations?.data ?: throw IllegalStateException("No generations found")
    }
}