package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.WorkType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZedCraftRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveAndObserveWorkItem_authenticatedOwner_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = ZedCraftFirestoreRepository(firestore, auth)

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveWorkItem(
                workType = WorkType.TITLE_OPTIMIZER,
                title = "10 YouTube Packaging Secrets",
                subtitle = "Score: 92/100",
                score = 92,
                payloadJson = "{\"score\":92}"
            )
        }
        assertTrue("Expected saveWorkItem to succeed: ${saveResult.exceptionOrNull()}", saveResult.isSuccess)
        val workId = saveResult.getOrThrow()

        val items = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeSavedWork().first { list -> list.any { it.id == workId } }
        }
        assertTrue(items.any { it.id == workId && it.score == 92 })
    }

    @Test
    fun getSavedWork_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ZedCraftFirestoreRepository(firestore, auth)
        val workId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveWorkItem(
                workType = WorkType.THUMBNAIL_SCORE,
                title = "Alice Thumbnail",
                subtitle = "Score: 88/100",
                score = 88,
                payloadJson = "{\"score\":88}"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = ZedCraftFirestoreRepository(firestore, auth)
        val bobReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getSavedWorkById(aliceUid, workId)
        }
        assertTrue("Expected cross-user read to fail", bobReadResult.isFailure)
        val firestoreEx = bobReadResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun unauthenticatedRead_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ZedCraftFirestoreRepository(firestore, auth)
        val workId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveWorkItem(
                workType = WorkType.VIDEO_AUDIT,
                title = "Audit",
                subtitle = "Score: 90",
                score = 90,
                payloadJson = "{\"score\":90}"
            ).getOrThrow()
        }

        auth.signOut()
        val unauthResult = runCatching {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("saved_work").document(workId).get().await()
            }
        }
        assertTrue("Expected unauthenticated read to fail", unauthResult.isFailure)
        val firestoreEx = unauthResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@zedcraft.test"
        const val BOB_EMAIL = "bob@zedcraft.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
