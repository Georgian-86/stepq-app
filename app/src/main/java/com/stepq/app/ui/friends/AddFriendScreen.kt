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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.stepq.app.ui.components.SkeletonList
import com.stepq.app.ui.components.appear
import com.stepq.app.ui.components.StepqAvatar
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.components.UnderlineField
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqDimens
import com.stepq.app.ui.theme.StepqType

/**
 * A13 Add friends: a search bar that finds people as you type (name or username), friends
 * from your contacts, and people you may know (friends of friends).
 */
@Composable
fun AddFriendScreen(
    myUsername: String,
    state: AddFriendState,
    relationOf: (String) -> Relation,
    onType: (String) -> Unit,
    onFindContacts: () -> Unit,
    onRequest: (PersonSummary) -> Unit,
    onAccept: (PersonSummary) -> Unit,
    onInvite: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val searching = state.query.trim().removePrefix("@").length >= 2
    Column(
        Modifier
            .fillMaxSize()
            .background(StepqColors.White)
            .statusBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = StepqColors.Ink)
            }
            Text("Add friends", style = StepqType.Subheading, color = StepqColors.Ink)
        }
        SearchBar(state.query, onType, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            if (searching) {
                when {
                    state.failed -> Note("Couldn't search right now. Try again.", error = true)
                    state.results == null -> SkeletonList(rows = 3, modifier = Modifier.padding(top = 8.dp))
                    state.results.isEmpty() -> Note("No one on StepQ matches \"${state.query.trim()}\". Check the spelling, or invite them.")
                    else -> state.results.forEachIndexed { i, r ->
                        Box(Modifier.appear(i)) { SuggestionRow(r, relationOf(r.id), state.query, onRequest, onAccept) }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                SectionTitle("From your contacts")
                when {
                    state.contactsLoading -> SkeletonList(rows = 2, modifier = Modifier.padding(top = 4.dp))
                    state.contacts == null -> ContactsCard(onFindContacts)
                    state.contacts.isEmpty() -> Note("None of your contacts are on StepQ yet. Invite them below.")
                    else -> state.contacts.forEachIndexed { i, r ->
                        Box(Modifier.appear(i)) { SuggestionRow(r, relationOf(r.id), "", onRequest, onAccept) }
                    }
                }
                if (state.mayKnow.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    SectionTitle("People you may know")
                    state.mayKnow.forEachIndexed { i, r ->
                        Box(Modifier.appear(i)) { SuggestionRow(r, relationOf(r.id), "", onRequest, onAccept) }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "Your username is @$myUsername. Friends can search for it, or you can send them an invite.",
                    style = StepqType.Small,
                    color = StepqColors.Muted,
                )
                Spacer(Modifier.height(12.dp))
                StepqButton("Invite friends to StepQ", onInvite, style = com.stepq.app.ui.components.StepqButtonStyle.Outline)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onType: (String) -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(StepqColors.Mint)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = StepqColors.Deep, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Search by name or @username", style = StepqType.Body, color = StepqColors.Muted)
            BasicTextField(
                value = query,
                onValueChange = { onType(it.take(40)) },
                singleLine = true,
                textStyle = StepqType.Body.copy(color = StepqColors.Ink),
                cursorBrush = SolidColor(StepqColors.Deep),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Search,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Search people" },
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Clear search",
                tint = StepqColors.Muted,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onType("") },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = StepqType.SmallStrong, color = StepqColors.Muted, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun Note(text: String, error: Boolean = false) {
    Text(text, style = StepqType.Body, color = if (error) StepqColors.TomatoText else StepqColors.Muted, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun ContactsCard(onFind: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StepqDimens.RadiusM))
            .background(StepqColors.LeafSoft)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Contacts, contentDescription = null, tint = StepqColors.Deep, modifier = Modifier.size(28.dp))
        Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
            Text("Find friends from your contacts", style = StepqType.BodyStrong, color = StepqColors.Ink)
            Text(
                "We check your contacts' email addresses against StepQ, scrambled first. Nothing is saved.",
                style = StepqType.Small,
                color = StepqColors.Muted,
            )
        }
        SmallPill("Allow", filled = true, onClick = onFind)
    }
}

/** A person to add, with the part that matches the search in bold green, Snapchat style. */
@Composable
private fun SuggestionRow(
    s: com.stepq.app.data.Suggestion,
    relation: Relation,
    query: String,
    onRequest: (PersonSummary) -> Unit,
    onAccept: (PersonSummary) -> Unit,
) {
    val q = query.trim().removePrefix("@").lowercase()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(StepqDimens.RadiusM))
            .background(if (relation == Relation.None || relation == Relation.AsksYou) StepqColors.Mint else StepqColors.White)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepqAvatar(s.avatar, 44.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(highlight(s.displayName, q), style = StepqType.BodyStrong, color = StepqColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildString {
                append("@").append(s.username)
                if (s.mutual > 0) append(" · ").append(s.mutual).append(if (s.mutual == 1L) " mutual friend" else " mutual friends")
            }
            Text(highlight(sub, q, from = 1), style = StepqType.Small, color = StepqColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when (relation) {
            Relation.None -> SmallPill("+ Add", filled = true) { onRequest(s.person) }
            Relation.AsksYou -> SmallPill("Accept", filled = true) { onAccept(s.person) }
            Relation.Requested -> Text("Requested", style = StepqType.SmallStrong, color = StepqColors.Muted)
            Relation.Friend -> Text("Friends", style = StepqType.SmallStrong, color = StepqColors.Deep)
            Relation.Me -> Text("You", style = StepqType.SmallStrong, color = StepqColors.Muted)
        }
    }
}

/** [text] with the first word starting with [q] (at or after [from]) in bold green. */
internal fun highlight(text: String, q: String, from: Int = 0): androidx.compose.ui.text.AnnotatedString {
    val b = androidx.compose.ui.text.AnnotatedString.Builder(text)
    if (q.isNotEmpty()) {
        val lower = text.lowercase()
        val at = generateSequence(lower.indexOf(q, from).takeIf { it >= 0 }) { i -> lower.indexOf(q, i + 1).takeIf { it >= 0 } }
            .firstOrNull { it == from || it == 0 || !lower[it - 1].isLetterOrDigit() }
        if (at != null) {
            b.addStyle(
                androidx.compose.ui.text.SpanStyle(color = StepqColors.Deep, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                at, at + q.length,
            )
        }
    }
    return b.toAnnotatedString()
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
