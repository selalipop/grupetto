package com.spop.poverlay.releases

import android.net.Uri
import okhttp3.*
import org.json.JSONArray
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ReleaseChecker(
    val client: OkHttpClient = OkHttpClient()
) {
    companion object {
        val GithubHeaderVersion = "2022-11-28"
        val GithubHeaderAccept = "application/vnd.github+json"
        val GithubFormatDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

        const val ReleaseEndpoint = "https://api.github.com/repos/selalipop/grupetto/releases"
    }

    init {

    }


    suspend fun getLatestRelease() = suspendCoroutine<Result<Release?>> {
        val request = Request.Builder().apply {
            url(ReleaseEndpoint)
            addHeader("X-GitHub-Api-Version", GithubHeaderVersion)
            addHeader("Accept", GithubHeaderAccept)
        }.build()
        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    it.resume(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseString = response.body?.string()
                    if (responseString == null) {
                        it.resume(Result.failure(Exception("No Response From Github")))
                    } else {
                        var latestRelease: Release? = null
                        try {
                            val result = JSONArray(responseString)
                            for (i in 0 until result.length()) {
                                val release = result.getJSONObject(i)
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
                            it.resume(Result.success(latestRelease))
                        } catch (e: Exception) {
                            it.resume(
                                Result.failure(Exception("failed to parse response from github", e))
                            )

                        }

                    }
                }

            }
        )
    }
}