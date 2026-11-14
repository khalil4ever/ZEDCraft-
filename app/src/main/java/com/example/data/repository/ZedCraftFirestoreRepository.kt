package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.ai.ZedCraftAiConfig
import com.example.data.model.CompetitorChannel
import com.example.data.model.ConnectedChannel
import com.example.data.model.SavedWorkItem
import com.example.data.model.UserProfile
import com.example.data.model.ZdChatMessage
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ZedCraftFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun safeId(raw: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        return cleaned.ifBlank { "id_${System.currentTimeMillis()}" }
    }

    // --- 1. User Profile ---
    fun observeUserProfile(): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertUserProfile(
        displayName: String,
        email: String = "",
        activeChannelId: String = "",
        activeChannelTitle: String = "",
        niche: String = ""
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val snap = docRef.get().await()
            val safeName = displayName.trim().ifBlank { "ZEDCraft Creator" }.take(100)
            if (!snap.exists()) {
                val createPayload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to safeName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (email.isNotBlank()) createPayload["email"] = email.take(200)
                if (activeChannelId.isNotBlank()) createPayload["activeChannelId"] = activeChannelId.take(120)
                if (activeChannelTitle.isNotBlank()) createPayload["activeChannelTitle"] = activeChannelTitle.take(200)
                if (niche.isNotBlank()) createPayload["niche"] = niche.take(120)
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mutableMapOf<String, Any>(
                    "displayName" to safeName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (email.isNotBlank()) updatePayload["email"] = email.take(200)
                if (activeChannelId.isNotBlank()) updatePayload["activeChannelId"] = activeChannelId.take(120)
                if (activeChannelTitle.isNotBlank()) updatePayload["activeChannelTitle"] = activeChannelTitle.take(200)
                if (niche.isNotBlank()) updatePayload["niche"] = niche.take(120)
                docRef.set(updatePayload, SetOptions.merge()).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // --- 2. Connected Channels ---
    fun observeConnectedChannels(): Flow<List<ConnectedChannel>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/channels"
        emitAll(
            db.collection("users").document(uid).collection("channels")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ConnectedChannel::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveConnectedChannel(channel: ConnectedChannel): Result<String> = runCatching {
        val uid = requireUserId()
        val docId = safeId(channel.channelId)
        val docRef = db.collection("users").document(uid).collection("channels").document(docId)
        try {
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "channelId" to channel.channelId.take(120),
                    "title" to channel.title.ifBlank { "YouTube Channel" }.take(200),
                    "handle" to channel.handle.take(120),
                    "thumbnailUrl" to channel.thumbnailUrl.take(500),
                    "subscriberCount" to channel.subscriberCount.coerceAtLeast(0L),
                    "viewCount" to channel.viewCount.coerceAtLeast(0L),
                    "videoCount" to channel.videoCount.coerceAtLeast(0L),
                    "healthScore" to channel.healthScore.coerceIn(0, 100),
                    "isOAuthConnected" to channel.isOAuthConnected,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "title" to channel.title.ifBlank { "YouTube Channel" }.take(200),
                    "handle" to channel.handle.take(120),
                    "thumbnailUrl" to channel.thumbnailUrl.take(500),
                    "subscriberCount" to channel.subscriberCount.coerceAtLeast(0L),
                    "viewCount" to channel.viewCount.coerceAtLeast(0L),
                    "videoCount" to channel.videoCount.coerceAtLeast(0L),
                    "healthScore" to channel.healthScore.coerceIn(0, 100),
                    "isOAuthConnected" to channel.isOAuthConnected,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(updatePayload, SetOptions.merge()).await()
            }
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun removeConnectedChannel(channelId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docId = safeId(channelId)
        val docRef = db.collection("users").document(uid).collection("channels").document(docId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 3. Saved Work & History ---
    fun observeSavedWork(): Flow<List<SavedWorkItem>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/saved_work"
        emitAll(
            db.collection("users").document(uid).collection("saved_work")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SavedWorkItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveWorkItem(
        workType: String,
        title: String,
        subtitle: String,
        score: Int,
        payloadJson: String,
        channelId: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("saved_work").document()
        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "workType" to workType,
            "title" to title.trim().ifBlank { "Untitled Optimization" }.take(300),
            "subtitle" to subtitle.take(500),
            "score" to score.coerceIn(0, 100),
            "payloadJson" to payloadJson.ifBlank { "{}" }.take(50000),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (channelId.isNotBlank()) {
            payload["channelId"] = channelId.take(120)
        }
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun getSavedWorkById(ownerUid: String, workId: String): Result<SavedWorkItem?> = runCatching {
        val docRef = db.collection("users").document(ownerUid).collection("saved_work").document(workId)
        try {
            val snap = docRef.get().await()
            snap.toObject(SavedWorkItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun deleteSavedWork(workId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("saved_work").document(workId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 4. Competitor Tracking ---
    fun observeCompetitors(): Flow<List<CompetitorChannel>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/competitors"
        emitAll(
            db.collection("users").document(uid).collection("competitors")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CompetitorChannel::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.subscriberCount }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addOrUpdateCompetitor(competitor: CompetitorChannel): Result<String> = runCatching {
        val uid = requireUserId()
        val docId = safeId(competitor.channelId)
        val docRef = db.collection("users").document(uid).collection("competitors").document(docId)
        try {
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "channelId" to competitor.channelId.take(120),
                    "title" to competitor.title.ifBlank { "Competitor Channel" }.take(200),
                    "handle" to competitor.handle.take(120),
                    "thumbnailUrl" to competitor.thumbnailUrl.take(500),
                    "subscriberCount" to competitor.subscriberCount.coerceAtLeast(0L),
                    "viewCount" to competitor.viewCount.coerceAtLeast(0L),
                    "videoCount" to competitor.videoCount.coerceAtLeast(0L),
                    "recentAvgViews" to competitor.recentAvgViews.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "title" to competitor.title.ifBlank { "Competitor Channel" }.take(200),
                    "handle" to competitor.handle.take(120),
                    "thumbnailUrl" to competitor.thumbnailUrl.take(500),
                    "subscriberCount" to competitor.subscriberCount.coerceAtLeast(0L),
                    "viewCount" to competitor.viewCount.coerceAtLeast(0L),
                    "videoCount" to competitor.videoCount.coerceAtLeast(0L),
                    "recentAvgViews" to competitor.recentAvgViews.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(updatePayload, SetOptions.merge()).await()
            }
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun removeCompetitor(channelId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docId = safeId(channelId)
        val docRef = db.collection("users").document(uid).collection("competitors").document(docId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 5. ZD Chat History ---
    fun observeZdChats(): Flow<List<ZdChatMessage>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/zd_chats"
        emitAll(
            db.collection("users").document(uid).collection("zd_chats")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ZdChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp.now() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun appendZdChatMessage(
        sessionId: String,
        role: String,
        content: String,
        skillId: String,
        modelUsed: String,
        toolCardJson: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("zd_chats").document()
        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "sessionId" to sessionId.ifBlank { "default" }.take(120),
            "role" to if (role == "model") "model" else "user",
            "content" to content.trim().ifBlank { "..." }.take(30000),
            "skillId" to skillId.ifBlank { "general" }.take(100),
            "modelUsed" to modelUsed.ifBlank { ZedCraftAiConfig.ZD_MAIN_MODEL }.take(100),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (toolCardJson.isNotBlank()) {
            payload["toolCardJson"] = toolCardJson.take(30000)
        }
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun clearZdChatHistory(): Result<Unit> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("zd_chats")
        try {
            val docs = colRef.get().await().documents
            for (doc in docs) {
                doc.reference.delete().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colRef.path)
            throw e
        }
    }

    suspend fun deleteAllUserData(): Result<Unit> = runCatching {
        val uid = requireUserId()
        val userDoc = db.collection("users").document(uid)
        try {
            for (sub in listOf("channels", "saved_work", "competitors", "zd_chats")) {
                val snap = userDoc.collection(sub).get().await()
                for (doc in snap.documents) {
                    doc.reference.delete().await()
                }
            }
            userDoc.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userDoc.path)
            throw e
        }
    }
}
