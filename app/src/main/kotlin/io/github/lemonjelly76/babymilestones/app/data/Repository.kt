package io.github.lemonjelly76.babymilestones.app.data

import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.content.ContentLoader
import io.github.lemonjelly76.babymilestones.core.content.ContentVersions

/**
 * Reads come from the server and are cached on the phone; when the server can't be reached the cached copy is used.
 * Writes go straight to the server and then refresh the affected baby.
 */
class Repository(val store: LocalStore, val api: ApiClient = ApiClient({ store.serverUrl }, { store.token })) {

    private val bundled: ContentBundle by lazy { ContentLoader.fromClasspath() }

    /** The newest content this phone has: a newer server bundle if one was downloaded, else the one shipped in the app. */
    fun content(): ContentBundle {
        val cached = store.readText(CONTENT)?.let { runCatching { api.parseContent(it) }.getOrNull() }
        return if (cached != null && ContentVersions.newer(cached.version, bundled.version)) cached else bundled
    }

    /** Downloads the server's content bundle when its version is newer than the one in use. */
    suspend fun refreshContent(current: ContentBundle): ContentBundle {
        val version = api.contentVersion()
        if (!ContentVersions.newer(version, current.version)) return current
        val text = api.contentText()
        val bundle = api.parseContent(text)
        store.writeText(CONTENT, text)
        return bundle
    }

    fun cachedMe(): MeResponse? = store.read(ME, MeResponse.serializer())
    fun cachedData(babyId: String): BabyData? = store.read(data(babyId), BabyData.serializer())

    suspend fun refreshMe(): MeResponse = api.me().also { store.write(ME, MeResponse.serializer(), it) }

    suspend fun refreshBaby(babyId: String): BabyData = api.babyData(babyId).also { store.write(data(babyId), BabyData.serializer(), it) }

    fun signIn(response: SignInResponse) {
        store.token = response.token
    }

    fun signOut() = store.clear()

    companion object {
        private const val ME = "me.json"
        private const val CONTENT = "content.json"
        private fun data(babyId: String) = "baby_$babyId.json"
    }
}
