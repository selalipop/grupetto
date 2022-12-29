package com.spop.poverlay.releases

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import ru.gildor.coroutines.okhttp.await
import java.text.SimpleDateFormat
import java.util.*

class ReleaseChecker(
    private val client: OkHttpClient = OkHttpClient()
) {
    companion object {
        const val GithubHeaderVersion = "2022-11-28"
        const val GithubHeaderAccept = "application/vnd.github+json"
        val GithubFormatDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

        const val ReleaseEndpoint = "https://api.github.com/repos/selalipop/grupetto/releases"
    }

    suspend fun getLatestRelease(): Result<Release?> {
        val request = Request.Builder().apply {
            url(ReleaseEndpoint)
            addHeader("X-GitHub-Api-Version", GithubHeaderVersion)
            addHeader("Accept", GithubHeaderAccept)
        }.build()
        try {
            val response = client.newCall(request).await()
            val responseString = response.body?.string()
                ?: return Result.failure(Exception("no response from github"))

            return Result.success(getLatestReleaseFromApiResponse(responseString))
        } catch (e: Exception) {
            return Result.failure(Exception("failed to parse response from github", e))
        }

    }

    private fun getLatestReleaseFromApiResponse(responseString: String): Release? {
        var latestRelease: Release? = null

        val responseArray = JSONArray(responseString)

        for (i in 0 until responseArray.length()) {
            val release = responseArray.getJSONObject(i)
            if (release.has("draft") && release.getBoolean("draft")) {
                continue
            }
            if (release.has("prerelease") && release.getBoolean("prerelease")) {
                continue
            }
            // Github Published At is more accurate than Created At
            val releaseDate =
                GithubFormatDate.parse(release.getString("published_at"))
                    ?: continue

            if (latestRelease == null
                || releaseDate.after(latestRelease.createdAt)
            ) {
                latestRelease = Release(
                    tagName = release.getString("tag_name").trim(),
                    friendlyName = release.getString("name").trim(),
                    createdAt = releaseDate,
                    url = Uri.parse(release.getString("html_url")),
                )
            }
        }
        return latestRelease
    }

}