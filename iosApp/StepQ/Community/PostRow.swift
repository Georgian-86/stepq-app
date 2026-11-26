import SwiftUI
import Shared

/// Hidden posts and blocked people for this session, and the report / block sheets.
@MainActor
@Observable
final class Moderation {
    var hiddenPosts: Set<String> = []
    var blockedPeople: Set<String> = []
    var reporting: Post?
    var blocking: Post?
    var deleting: Post?
    var note: String?

    func shows(_ p: Post) -> Bool { !hiddenPosts.contains(p.id) && !blockedPeople.contains(p.authorId) }
}

struct PostRow: View {
    @Environment(AppModel.self) private var model
    let post: Post
    let moderation: Moderation
    let onChange: (Post) -> Void

    var body: some View {
        let mine = post.authorId == model.myId
        HStack(alignment: .top, spacing: 12) {
            AvatarView(config: post.authorAvatar, size: 40)
            VStack(alignment: .leading, spacing: 6) {
                if let by = post.repostedBy {
                    Label("\(by) reposted", systemImage: "arrow.2.squarepath").font(Typo.small).foregroundStyle(Palette.muted)
                }
                HStack(spacing: 6) {
                    Text(post.authorName).font(Typo.bodyStrong).foregroundStyle(Palette.ink).lineLimit(1)
                    Text("@\(post.authorUsername)").font(Typo.small).foregroundStyle(Palette.muted).lineLimit(1)
                    Spacer(minLength: 4)
                    if let d = parseTimestamp(post.createdAt) {
                        Text(d, format: .relative(presentation: .named)).font(Typo.small).foregroundStyle(Palette.muted)
                    }
                    menu(mine: mine)
                }
                if post.pending {
                    Label("Waiting for approval", systemImage: "clock").font(Typo.smallStrong).foregroundStyle(Palette.goldText)
                }
                if let replyTo = post.parentUsername {
                    Text("Replying to @\(replyTo)").font(Typo.small).foregroundStyle(Palette.muted)
                }
                if !post.body.isEmpty {
                    Text(post.body).font(Typo.body).foregroundStyle(Palette.ink).fixedSize(horizontal: false, vertical: true)
                }
                if post.isAchievement, let steps = post.steps {
                    HStack {
                        Image(systemName: post.goalReached ? "checkmark.seal.fill" : "figure.walk").foregroundStyle(Palette.deep)
                        Text("\(formatSteps(steps.intValue)) steps" + (post.goalReached ? " · goal reached" : ""))
                            .font(Typo.bodyStrong).foregroundStyle(Palette.deep)
                    }
                    .padding(10)
                    .background(Palette.leafSoft, in: RoundedRectangle(cornerRadius: 12))
                }
                HStack(spacing: 22) {
                    Button { Task { await toggleLike() } } label: {
                        Label("\(post.likeCount)", systemImage: post.liked ? "heart.fill" : "heart")
                            .foregroundStyle(post.liked ? Palette.tomato : Palette.muted)
                    }
                    .accessibilityLabel(post.liked ? "Unlike, \(post.likeCount) likes" : "Like, \(post.likeCount) likes")
                    Label("\(post.replyCount)", systemImage: "bubble.left").foregroundStyle(Palette.muted)
                        .accessibilityLabel("\(post.replyCount) replies")
                    Button { Task { await toggleRepost() } } label: {
                        Label("\(post.repostCount)", systemImage: "arrow.2.squarepath")
                            .foregroundStyle(post.reposted ? Palette.deep : Palette.muted)
                    }
                    .accessibilityLabel(post.reposted ? "Undo repost" : "Repost")
                }
                .font(Typo.small)
                .buttonStyle(.borderless)
            }
        }
        .padding(.vertical, 6)
    }

    private func menu(mine: Bool) -> some View {
        Menu {
            if mine {
                Button("Delete post", systemImage: "trash", role: .destructive) { moderation.deleting = post }
            } else {
                Button("Report post", systemImage: "flag") { moderation.reporting = post }
                Button("Block @\(post.authorUsername)", systemImage: "hand.raised") { moderation.blocking = post }
            }
        } label: {
            Image(systemName: "ellipsis").foregroundStyle(Palette.muted).frame(width: 28, height: 28)
        }
        .accessibilityLabel("More options")
    }

    private func toggleLike() async {
        let next = post.toggledLike()
        onChange(next)
        do { try await model.api.setLiked(postId: post.id, liked: next.liked) } catch { onChange(post) }
    }

    private func toggleRepost() async {
        let next = post.toggledRepost()
        onChange(next)
        do { try await model.api.setReposted(postId: post.id, reposted: next.reposted) } catch { onChange(post) }
    }
}

/// Report, block and delete confirmations, shared by the feed and threads.
struct ModerationSheets: ViewModifier {
    @Environment(AppModel.self) private var model
    @Bindable var moderation: Moderation

    func body(content: Content) -> some View {
        content
            .sheet(item: Binding(get: { moderation.reporting.map(PostRef.init) }, set: { if $0 == nil { moderation.reporting = nil } })) { ref in
                ReportSheet(post: ref.post) { reason in
                    Task {
                        do {
                            try await model.api.reportPost(postId: ref.post.id, reason: reason)
                            moderation.hiddenPosts.insert(ref.post.id)
                            moderation.note = "Thanks for reporting. The post is hidden for you, and StepQ reviews reports within 24 hours."
                        } catch {
                            moderation.note = error.localizedDescription
                        }
                        moderation.reporting = nil
                    }
                }
            }
            .confirmationDialog("Block this person?", isPresented: Binding(get: { moderation.blocking != nil }, set: { if !$0 { moderation.blocking = nil } }), presenting: moderation.blocking) { p in
                Button("Block @\(p.authorUsername)", role: .destructive) {
                    Task {
                        do {
                            try await model.api.blockUser(userId: p.authorId)
                            moderation.blockedPeople.insert(p.authorId)
                            moderation.note = "You blocked @\(p.authorUsername). You won't see each other's posts. Unblock any time in Profile → Privacy and data."
                        } catch {
                            moderation.note = error.localizedDescription
                        }
                    }
                }
            } message: { p in
                Text("You won't see @\(p.authorUsername)'s posts and they won't see yours. To report something they posted, use Report post.")
            }
            .confirmationDialog("Delete this post?", isPresented: Binding(get: { moderation.deleting != nil }, set: { if !$0 { moderation.deleting = nil } }), presenting: moderation.deleting) { p in
                Button("Delete", role: .destructive) {
                    Task {
                        do {
                            try await model.api.deletePost(postId: p.id)
                            moderation.hiddenPosts.insert(p.id)
                        } catch {
                            moderation.note = error.localizedDescription
                        }
                    }
                }
            } message: { _ in
                Text("Its replies, likes and reposts go too.")
            }
    }
}

private struct PostRef: Identifiable {
    let post: Post
    var id: String { post.id }
}

struct ReportSheet: View {
    @Environment(\.dismiss) private var dismiss
    let post: Post
    let send: (String) -> Void
    @State private var other = ""

    private let reasons = [
        "Spam or scam",
        "Bullying or harassment",
        "Hate speech or symbols",
        "Nudity or sexual content",
        "Violence or threats",
        "Self-harm or suicide",
        "Someone under 13",
        "Something else",
    ]

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(reasons, id: \.self) { r in
                        Button(r) { send(r == "Something else" && !other.isEmpty ? other : r) }
                            .foregroundStyle(Palette.ink)
                    }
                } header: {
                    Text("Why are you reporting @\(post.authorUsername)'s post?")
                } footer: {
                    Text("Reports are private. If someone is in danger, contact local emergency services.")
                }
                Section("Anything else? (optional)") {
                    TextField("Tell us more", text: $other, axis: .vertical).lineLimit(2...4)
                }
            }
            .navigationTitle("Report")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
        }
    }
}
