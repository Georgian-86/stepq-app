package com.stepq.app.ui.friends

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stepq.app.data.PersonSummary
import com.stepq.app.ui.components.StepqAvatar
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.components.UnderlineField
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqDimens
import com.stepq.app.ui.theme.StepqType

/** Find someone by their exact username and send a friend request (A13 add-friend button). */
@Composable
fun AddFriendScreen(
    myUsername: String,
    result: SearchResult,
    onSearch: (String) -> Unit,
    onTyping: () -> Unit,
    onRequest: (PersonSummary) -> Unit,
    onAccept: (PersonSummary) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var query by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(StepqColors.White)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 0.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = StepqColors.Ink)
        }
        Spacer(Modifier.height(8.dp))
        Text("Add a friend", style = StepqType.Title, color = StepqColors.Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "Ask for their StepQ username. Yours is @$myUsername.",
            style = StepqType.Body,
            color = StepqColors.Muted,
        )
        Spacer(Modifier.height(28.dp))
        UnderlineField(
            label = "Username",
            value = query,
            onValueChange = {
                query = it.trim().lowercase()
                onTyping()
            },
            placeholder = "e.g. riya.walks",
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
        )
        Spacer(Modifier.height(20.dp))
        StepqButton(
            "Search",
            { onSearch(query) },
            enabled = query.removePrefix("@").length >= 3,
            loading = result == SearchResult.Searching,
        )
        Spacer(Modifier.height(24.dp))
        when (result) {
            SearchResult.Idle, SearchResult.Searching -> Unit
            SearchResult.NotFound -> Text(
                "No one is called @${query.removePrefix("@")}. Check the spelling with them.",
                style = StepqType.Body,
                color = StepqColors.Muted,
            )
            is SearchResult.Failed -> Text(result.message, style = StepqType.Body, color = StepqColors.TomatoText)
            is SearchResult.Found -> PersonCard(result.person, result.relation, onRequest, onAccept)
        }
    }
}

@Composable
private fun PersonCard(
    person: PersonSummary,
    relation: Relation,
    onRequest: (PersonSummary) -> Unit,
    onAccept: (PersonSummary) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StepqDimens.RadiusM))
            .background(StepqColors.Mint)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepqAvatar(person.avatar, 48.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(person.displayName, style = StepqType.BodyStrong, color = StepqColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@${person.username}", style = StepqType.Small, color = StepqColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when (relation) {
            Relation.None -> SmallPill("Add", filled = true) { onRequest(person) }
            Relation.AsksYou -> SmallPill("Accept", filled = true) { onAccept(person) }
            Relation.Requested -> Text("Requested", style = StepqType.SmallStrong, color = StepqColors.Muted)
            Relation.Friend -> Text("Friends", style = StepqType.SmallStrong, color = StepqColors.Deep)
            Relation.Me -> Text("That's you", style = StepqType.SmallStrong, color = StepqColors.Muted)
        }
    }
}
