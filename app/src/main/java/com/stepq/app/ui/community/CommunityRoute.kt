package com.stepq.app.ui.community

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stepq.app.data.CommunityRepository
import com.stepq.app.data.Post
import com.stepq.app.ui.friends.Toast
import com.stepq.app.ui.friends.shareText
import kotlinx.coroutines.delay

/**
 * The Community section of the Friends tab: the feed, and its full-screen pages (a thread,
 * the composer). Pages stack, so opening a reply's own thread and going back returns to the
 * thread it was in.
 */
class CommunitySection internal constructor(
    private val vm: CommunityViewModel,
    private val state: State<CommunityUiState>,
    private val stack: MutableState<String>,
    private val options: MutableState<Post?>,
    private val share: (Post) -> Unit,
    private val copy: (Post) -> Unit,
) {
    private val pages: List<String> get() = stack.value.split('|').filter { it.isNotEmpty() }

    /** null for the feed, else "compose", "compose-steps", "post:<id>" or "reply:<id>". */
    val page: String? get() = pages.lastOrNull()

    private fun push(p: String) {
        stack.value = (pages + p).takeLast(MAX_DEPTH).joinToString("|")
        p.postId()?.let(vm::openThread)
        if (p.startsWith("compose")) vm.openComposer()
    }

    fun back() {
        val rest = pages.dropLast(1)
        stack.value = rest.joinToString("|")
        val top = rest.lastOrNull()?.postId()
        if (top != null) vm.openThread(top) else vm.closeThread()
        vm.clearComposeError()
    }

    private val actions = PostActions(
        onOpen = { push("post:${it.id}") },
        onLike = vm::toggleLike,
        onReply = { push("reply:${it.id}") },
        onRepost = vm::toggleRepost,
        onShare = share,
        onMore = { options.value = it },
    )

    @Composable
    fun Feed() {
        CommunityFeed(
            state.value,
            actions,
            onScope = vm::setScope,
            onCompose = { steps ->
                // Free members get a few new threads a month (replies and likes are unlimited).
                if (com.stepq.app.data.Pro.plan.value?.postsLeft == 0) {
                    com.stepq.app.ui.components.ProSheets.show(com.stepq.app.data.MyPlan.F.COMMUNITY_POSTS)
                } else {
                    push(if (steps) "compose-steps" else "compose")
                }
            },
            onRefresh = vm::refresh,
            onLoadMore = vm::loadMore,
        )
    }

    @Composable
    fun Page(page: String) {
        val s = state.value
        when {
            page.startsWith("compose") -> ComposeScreen(
                me = s.me,
                stepsToday = s.stepsToday,
                startWithSteps = page == "compose-steps",
                posting = s.posting,
                error = s.composeError,
                onPost = { body, steps -> vm.post(body, shareSteps = steps) { back() } },
                onTyping = vm::clearComposeError,
                onCancel = ::back,
            )
            else -> {
                val id = page.postId() ?: return
                val thread = s.thread?.takeIf { it.postId == id } ?: ThreadState(id)
                ThreadScreen(
                    state = thread,
                    me = s.me,
                    actions = actions,
                    posting = s.posting,
                    error = s.composeError,
                    onReply = { body, done -> vm.post(body, parentId = id) { done() } },
                    onTyping = vm::clearComposeError,
                    onBack = ::back,
                    focusReply = page.startsWith("reply:"),
                )
            }
        }
    }

    /** The "…" sheet and the confirmation toast, drawn over whatever page is showing. */
    @Composable
    fun Overlays(modifier: Modifier = Modifier) {
        options.value?.let { post ->
            PostOptionsSheet(
                post = post,
                mine = post.authorId == state.value.me?.id,
                onCopy = { copy(post) },
                onShare = { share(post) },
                onDelete = {
                    vm.delete(post)
                    if (page?.postId() == post.id) back()
                },
                onReport = { vm.report(post, it) },
                onDismiss = { options.value = null },
            )
        }
        state.value.toast?.let { message ->
            LaunchedEffect(message) {
                delay(2_500)
                vm.toastShown()
            }
            Toast(message, modifier, icon = Icons.Outlined.CheckCircle)
        }
    }

    private companion object {
        const val MAX_DEPTH = 12
    }
}

private fun String.postId(): String? = when {
    startsWith("post:") -> removePrefix("post:")
    startsWith("reply:") -> removePrefix("reply:")
    else -> null
}

@Composable
fun CommunitySection(repo: CommunityRepository, visible: Boolean): CommunitySection {
    val context = LocalContext.current
    val vm: CommunityViewModel = viewModel { CommunityViewModel(repo) }
    val state = vm.state.collectAsState()
    val stack = rememberSaveable { mutableStateOf("") }
    val options = remember { mutableStateOf<Post?>(null) }

    // After the app was recreated, reload the thread a restored page shows.
    LaunchedEffect(Unit) {
        stack.value.split('|').lastOrNull()?.postId()?.let { if (vm.state.value.thread?.postId != it) vm.openThread(it) }
    }
    // Load when the section is first shown, and again each time the app comes back to it.
    LaunchedEffect(visible) { if (visible && state.value.feed.posts.isEmpty()) vm.refresh() }
    LifecycleResumeEffect(visible) {
        if (visible && state.value.feed.loaded) vm.refresh()
        onPauseOrDispose { }
    }

    return remember(vm) {
        CommunitySection(
            vm, state, stack, options,
            share = { shareText(context, postShareText(it)) },
            copy = { post ->
                val clip = context.getSystemService(android.content.ClipboardManager::class.java)
                clip?.setPrimaryClip(android.content.ClipData.newPlainText("StepQ post", post.body))
                if (android.os.Build.VERSION.SDK_INT < 33) vm.showToast("Copied")
            },
        )
    }
}
