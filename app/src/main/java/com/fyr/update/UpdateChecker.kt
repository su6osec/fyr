package com.fyr.update

import android.content.Context
import android.content.SharedPreferences
import com.fyr.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The one place Fyr talks to the network, and what it says when it does.
 *
 * Fyr ships as a sideloaded APK, so there is no store to tell it that a newer
 * build exists. The answer lives in a single JSON document at [VERSION_URL]
 * in the repository, and this reads exactly that — one HTTPS GET, no cookies,
 * no identifiers, no body, to a host that is pinned in the source rather than
 * configured anywhere. The response is a version number, so the request
 * carries nothing about the person and the reply tells it nothing.
 *
 * The verdict is also written to a preferences file of its own. That is what
 * makes the gate enforceable rather than merely requested: once a build has
 * been told it is behind, it blocks from that record alone, so the answer
 * survives the radio being off, the timeout, and GitHub being down. The record
 * is dropped the moment a check succeeds and the running build already
 * satisfies it — which is what makes installing the update the thing that
 * unlocks the app, without anyone having to clear anything by hand.
 *
 * Every failure mode is a refusal rather than a crash: a missing host, a
 * non-2xx, a truncated body, a document with no `versionCode`. Fyr would
 * rather open than fall over on a flaky connection, because a habit tracker
 * that refuses to start on a train is worse than one that skips an update
 * notice for a day.
 */
object UpdateChecker {

    /**
     * Where the current version is published.
     *
     * `main` and not a tag, deliberately: bumping the file has to be a push
     * and not a release, because it is the release that this document is
     * announcing. Raw GitHub is served over HTTPS from a pinned host, and the
     * file itself is public — there is no credential here to steal.
     */
    const val VERSION_URL =
        "https://raw.githubusercontent.com/su6osec/fyr/main/releases/version.json"

    /**
     * Where "Update now" goes if the document does not name a URL of its own.
     * The releases page rather than a direct `.apk`, because that is the page
     * a person can always be talked through over the phone.
     */
    const val RELEASES_URL = "https://github.com/su6osec/fyr/releases/latest"

    /** What the document says about a build newer than this one. */
    data class Release(
        val versionCode: Int,
        val versionName: String,
        val url: String,
        val notes: String,
    ) {
        fun toJson(): String = JSONObject()
            .put("versionCode", versionCode)
            .put("versionName", versionName)
            .put("url", url)
            .put("notes", notes)
            .toString()
    }

    sealed interface Result {
        /** The running build is the one being pointed at. */
        data object Current : Result

        /** Something newer exists. */
        data class Required(val release: Release) : Result

        /** Nothing could be established — no answer is not an answer. */
        data object Unreachable : Result
    }

    private const val PREFS = "fyr_update"
    private const val KEY_PENDING = "pending"

    /** Long enough to be honest about a train tunnel, short enough not to be a hang. */
    private const val TIMEOUT_MS = 5_000

    /**
     * The verdict already on the ground, if any.
     *
     * Read for its side effect of answering instantly: a build that knows it
     * is behind should not wait on a round trip to say so. It is *not* by
     * itself permission to block — the caller has to check that the record
     * actually outranks [BuildConfig.VERSION_CODE], which is what stops an
     * updated install from inheriting the previous build's lock-out.
     */
    fun pending(context: Context): Release? =
        prefs(context).getString(KEY_PENDING, null)?.let(::decode)

    /**
     * Asks the document whether this build is current.
     *
     * Runs on [Dispatchers.IO]; every failure resolves to [Result.Unreachable]
     * rather than throwing, so this can be called from a launch path without
     * a try/catch around it.
     */
    suspend fun check(context: Context): Result = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val remote = fetch()

        if (remote == null) {
            // Unreachable: fall back to what is on the ground. A record that
            // this build already satisfies is not a record any more — it was
            // written by the build the person has now replaced — so it goes.
            val known = pending(app)
            return@withContext when {
                known == null -> Result.Unreachable
                known.versionCode > BuildConfig.VERSION_CODE -> Result.Required(known)
                else -> {
                    clear(app)
                    Result.Unreachable
                }
            }
        }

        if (remote.versionCode > BuildConfig.VERSION_CODE) {
            prefs(app).edit().putString(KEY_PENDING, encode(remote)).apply()
            Result.Required(remote)
        } else {
            // The whole point of the record: installing the update clears it,
            // on this build, without a flag day or a migration.
            clear(app)
            Result.Current
        }
    }

    private fun clear(context: Context) {
        prefs(context).edit().remove(KEY_PENDING).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * The GET. Returns `null` for every outcome that is not a readable
     * document — which includes a redirect to a 404, a rate-limit, and a
     * body that parses as JSON but carries no usable version.
     */
    private fun fetch(): Release? = try {
        val conn = URL(VERSION_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) null
            else conn.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { decode(it.readText()) }
        } finally {
            conn.disconnect()
        }
    } catch (_: Exception) {
        // Deliberately broad. This runs before the first frame on every cold
        // start, on whatever network the phone happens to be standing on, and
        // the price of letting a TLS handshake failure reach the caller is an
        // app that will not open. Nothing here is worth a stack trace.
        null
    }

    private fun decode(raw: String): Release? = try {
        val json = JSONObject(raw)
        val code = json.optInt("versionCode", -1)
        if (code <= 0) null
        else Release(
            versionCode = code,
            versionName = json.optString("versionName", code.toString()),
            url = json.optString("url", RELEASES_URL).ifBlank { RELEASES_URL },
            notes = json.optString("notes", ""),
        )
    } catch (_: Exception) {
        null
    }

    private fun encode(release: Release): String = release.toJson()
}
