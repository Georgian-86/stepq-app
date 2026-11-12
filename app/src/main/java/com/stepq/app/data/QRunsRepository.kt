package com.stepq.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** One row of app.qrun_feed(): a QRun with its host's name and how many are going (A18, A21). */
@Serializable
data class QRun(
    val id: String,
    val title: String,
    @SerialName("place_name") val placeName: String,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("starts_at") val startsAtRaw: String,
    @SerialName("created_at") val createdAtRaw: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("host_name") val hostName: String,
    @SerialName("host_avatar") val hostAvatar: AvatarConfig = AvatarConfig(),
    val going: Int = 0,
    val joined: Boolean = false,
) {
    val startsAt: Instant get() = ChallengesRepository.parseInstant(startsAtRaw)
    val createdAt: Instant get() = ChallengesRepository.parseInstant(createdAtRaw)
}

/** A place picked on A19: its name and, when the phone could find it, where it is. */
@Serializable
data class Place(val name: String, val lat: Double? = null, val lng: Double? = null)

@Serializable
private data class NewQRun(
    @SerialName("created_by") val createdBy: String,
    val title: String,
    @SerialName("place_name") val placeName: String,
    val lat: Double?,
    val lng: Double?,
    @SerialName("starts_at") val startsAt: String,
)

@Serializable
private data class QRunId(val id: String)

@Serializable
private data class Participant(@SerialName("qrun_id") val qrunId: String, @SerialName("user_id") val userId: String)

data class QRunsData(val myId: String, val runs: List<QRun>)

/**
 * QRuns (A18–A21). Anyone signed in can see QRuns and post or join as themselves; RLS blocks
 * posting in the past, posting as someone else, and editing or deleting others' QRuns.
 */
class QRunsRepository(private val client: SupabaseClient?) {

    private fun client() = client ?: throw NotConfiguredException()
    private fun myId() = client().auth.currentUserOrNull()?.id ?: throw IllegalStateException("Not signed in")

    suspend fun load(): Result<QRunsData> = runCatching {
        val runs = client().postgrest.rpc("qrun_feed").decodeList<QRun>()
        QRunsData(myId(), runs)
    }

    /** A19: posts the QRun and joins the host to it. Returns the new QRun's id. */
    suspend fun post(title: String, place: Place, startsAt: Instant): Result<String> = runCatching {
        val db = client().postgrest
        val me = myId()
        val id = db["qruns"].insert(NewQRun(me, title.trim(), place.name.trim(), place.lat, place.lng, startsAt.toString())) {
            select(io.github.jan.supabase.postgrest.query.Columns.list("id"))
        }.decodeSingle<QRunId>().id
        db["qrun_participants"].insert(Participant(id, me))
        id
    }

    /** A20: joining is only offered once the disclaimer box is ticked. */
    suspend fun join(qrunId: String): Result<Unit> = runCatching {
        client().postgrest["qrun_participants"].insert(Participant(qrunId, myId()))
    }

    suspend fun leave(qrunId: String): Result<Unit> = runCatching {
        client().postgrest["qrun_participants"].delete {
            filter {
                eq("qrun_id", qrunId)
                eq("user_id", myId())
            }
        }
    }

    /** The host cancels their QRun; everyone's place in it goes with it. */
    suspend fun cancel(qrunId: String): Result<Unit> = runCatching {
        client().postgrest["qruns"].delete { filter { eq("id", qrunId) } }
    }
}
