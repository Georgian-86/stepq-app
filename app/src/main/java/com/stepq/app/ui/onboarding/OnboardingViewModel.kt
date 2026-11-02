package com.stepq.app.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.stepq.app.data.AvatarConfig
import com.stepq.app.data.Gender
import com.stepq.app.data.NewProfile
import com.stepq.app.data.ProfileRepository
import java.time.LocalDate

/**
 * Answers collected across A06–A09. Nothing is written until A09, when the whole profile is
 * inserted in one go, so backing out halfway leaves no half-made row.
 */
class OnboardingViewModel : ViewModel() {
    var fullName by mutableStateOf("")
    var username by mutableStateOf("")
    var gender by mutableStateOf<Gender?>(null)
    var birthdate by mutableStateOf<LocalDate?>(null)
    var avatar by mutableStateOf(AvatarConfig())
    var dailyGoal by mutableIntStateOf(8000)

    val firstName: String get() = fullName.trim().substringBefore(' ').ifEmpty { "there" }

    suspend fun save(profiles: ProfileRepository): Result<Unit> {
        val id = profiles.currentUserId() ?: return Result.failure(IllegalStateException("Not signed in"))
        return profiles.create(
            NewProfile(
                id = id,
                displayName = fullName.trim(),
                username = username,
                gender = gender?.value,
                birthdate = birthdate?.toString(),
                avatar = avatar,
                dailyGoal = dailyGoal,
            ),
        )
    }
}
