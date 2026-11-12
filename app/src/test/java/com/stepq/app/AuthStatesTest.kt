package com.stepq.app

import com.stepq.app.data.AuthState
import com.stepq.app.data.authStates
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthStatesTest {
    private val session = UserSession(accessToken = "a", refreshToken = "r", expiresIn = 3600, tokenType = "bearer", user = null)
    private val signedIn = SessionStatus.Authenticated(session, SessionSource.Storage)
    private val offline = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(RuntimeException("offline")))

    private fun states(vararg s: SessionStatus) = runBlocking { authStates(flowOf(*s)).toList() }

    @Test fun offlineRefreshKeepsYouSignedIn() {
        assertEquals(listOf(AuthState.Loading, AuthState.SignedIn), states(SessionStatus.Initializing, offline))
    }

    @Test fun backgroundingDoesNotLeaveTheScreen() {
        // Supabase goes back to Initializing on every trip to the background.
        assertEquals(
            listOf(AuthState.Loading, AuthState.SignedIn),
            states(SessionStatus.Initializing, signedIn, SessionStatus.Initializing, offline, signedIn),
        )
    }

    @Test fun realSignOutSignsOut() {
        assertEquals(
            listOf(AuthState.Loading, AuthState.SignedIn, AuthState.SignedOut),
            states(SessionStatus.Initializing, signedIn, SessionStatus.NotAuthenticated(true)),
        )
    }
}
