import SwiftUI
import Shared

/// The community feed (Threads style), with what App Review Guideline 1.2 asks of apps with
/// user-generated content:
/// - People agree to the community rules (no tolerance for objectionable content or abusive
///   people) before they can see or post anything.
/// - A filter on the server refuses posts with banned words (migration 20261128000000).
/// - Any post can be reported; it disappears for the reporter at once, and three reports hide it
///   for everyone until a StepQ admin reviews it.
/// - Anyone can be blocked; their posts leave the feed straight away, both ways.
/// - StepQ's contact details are in Profile → Help.
struct CommunityView: View {
    @Environment(AppModel.self) private var model
    @AppStorage("community.rules.agreed") private var agreedVersion = ""

    var body: some View {
        NavigationStack {
            Group {
                if agreedVersion == Legal.communityRulesVersion + ":" + (model.myId ?? "") {
                    FeedView()
                } else {
                    CommunityRulesView { agreedVersion = Legal.communityRulesVersion + ":" + (model.myId ?? "") }
                }
            }
            .pageBackground()
            .navigationTitle("Community")
        }
    }
}

/// The rules people agree to before using the community.
struct CommunityRulesView: View {
    @Environment(AppModel.self) private var model
    let agreed: () -> Void
    @State private var busy = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack { Spacer(); Piko(pose: .cheer, size: 100); Spacer() }
                Text("Community rules").font(Typo.title()).foregroundStyle(Palette.ink)
                Text("StepQ's community is for cheering each other on. To keep it friendly:").font(Typo.body).foregroundStyle(Palette.muted)
                rule("hand.thumbsup.fill", "Be kind. No bullying, harassment, hate or threats.")
                rule("eye.slash.fill", "No sexual, violent or graphic content, and nothing illegal.")
                rule("megaphone.fill", "No spam, scams or selling.")
                rule("lock.fill", "Don't share anyone's private details, including your own.")
                rule("flag.fill", "Report anything that breaks these rules. We review reports within 24 hours and remove content and accounts that break them.")
                Text("There is zero tolerance for objectionable content or abusive people. Breaking these rules can get your posts removed and your account suspended.")
                    .font(Typo.bodyStrong).foregroundStyle(Palette.ink)
                Link("Read the full community rules", destination: Legal.communityRules).font(Typo.bodyStrong)
                Button {
                    Task {
                        busy = true
                        defer { busy = false }
                        try? await model.api.recordConsent(purpose: "community", version: Legal.communityRulesVersion, granted: true)
                        agreed()
                    }
                } label: {
                    if busy { ProgressView().tint(.white) } else { Text("I agree") }
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(busy)
                .padding(.top, 8)
            }
            .padding(24)
        }
    }

    private func rule(_ icon: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon).foregroundStyle(Palette.deep).frame(width: 24)
            Text(text).font(Typo.body).foregroundStyle(Palette.ink)
        }
    }
}

/// Feed scopes, as app.community_feed() takes them.
enum FeedScopeChoice: String, CaseIterable, Identifiable {
    case everyone, friends, mine
    var id: String { rawValue }
    var label: String {
        switch self {
        case .everyone: return "For you"
        case .friends: return "Friends"
        case .mine: return "Your posts"
        }
    }
}

struct FeedView: View {
    @Environment(AppModel.self) private var model
    @State private var scope: FeedScopeChoice = .everyone
    @State private var posts: [Post] = []
    @State private var loading = true
    @State private var exhausted = false
    @State private var error: String?
    @State private var composing = false
    @State private var showUpgrade = false
    @State private var moderation = Moderation()
    private let seeded: Bool

    init(seed: [Post]? = nil) {
        _posts = State(initialValue: seed ?? [])
        _loading = State(initialValue: seed == nil)
        seeded = seed != nil
    }

    var body: some View {
        VStack(spacing: 0) {
            Picker("Show", selection: $scope) {
                ForEach(FeedScopeChoice.allCases) { Text($0.label).tag($0) }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            List {
                if let error { Text(error).font(Typo.small).foregroundStyle(Palette.tomatoText) }
                if let note = moderation.note { Text(note).font(Typo.small).foregroundStyle(Palette.deep) }
                ForEach(visible, id: \.id) { post in
                    NavigationLink(value: post.id) {
                        PostRow(post: post, moderation: moderation, onChange: replace)
                    }
                    .onAppear { if post.id == visible.last?.id { Task { await more() } } }
                }
                if loading { HStack { Spacer(); ProgressView(); Spacer() } }
                if !loading && visible.isEmpty {
                    Text(scope == .mine ? "You haven't posted yet." : "Nothing here yet. Say hi!").font(Typo.body).foregroundStyle(Palette.muted)
                }
            }
            .listStyle(.plain)
            .refreshable { await reload() }
        }
        .navigationDestination(for: String.self) { id in ThreadView(postId: id, moderation: moderation) }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    if canPost { composing = true } else { showUpgrade = true }
                } label: { Label("New post", systemImage: "square.and.pencil") }
            }
        }
        .sheet(isPresented: $composing, onDismiss: { Task { await reload() } }) { ComposeView(parent: nil) }
        .sheet(isPresented: $showUpgrade) { PaywallView(reason: "Starting posts is for StepQ Pro members. You can still like and reply.") }
        .modifier(ModerationSheets(moderation: moderation))
        .task(id: scope) { await reload() }
    }

    private var visible: [Post] { posts.filter { moderation.shows($0) } }

    /// Free members can like and reply; starting posts depends on the plan (Postgres decides).
    private var canPost: Bool {
        guard let plan = model.plan else { return true }
        if let left = plan.postsLeft { return left.intValue > 0 }
        return true
    }

    private func reload() async {
        if seeded { return }
        loading = true
        exhausted = false
        defer { loading = false }
        do {
            posts = try await model.api.feed(scope: scope.rawValue, before: nil)
            exhausted = posts.count < 25
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func more() async {
        guard !loading, !exhausted, let last = posts.last?.entryAt else { return }
        loading = true
        defer { loading = false }
        if let next = try? await model.api.feed(scope: scope.rawValue, before: last) {
            posts += next.filter { n in !posts.contains { $0.id == n.id && $0.entryAt == n.entryAt } }
            exhausted = next.count < 25
        }
    }

    private func replace(_ p: Post) {
        if let i = posts.firstIndex(where: { $0.id == p.id }) { posts[i] = p }
    }
}

/// A post with what it replies to above and its replies below, and a reply box.
struct ThreadView: View {
    @Environment(AppModel.self) private var model
    let postId: String
    let moderation: Moderation
    @State private var posts: [Post] = []
    @State private var error: String?
    @State private var replying = false

    var body: some View {
        List {
            if let error { Text(error).font(Typo.small).foregroundStyle(Palette.tomatoText) }
            ForEach(posts.filter { moderation.shows($0) }, id: \.id) { p in
                PostRow(post: p, moderation: moderation, onChange: { n in
                    if let i = posts.firstIndex(where: { $0.id == n.id }) { posts[i] = n }
                })
                .padding(.leading, p.depth > 0 ? 24 : 0)
            }
        }
        .listStyle(.plain)
        .navigationTitle("Thread")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { replying = true } label: { Label("Reply", systemImage: "arrowshape.turn.up.left") }
            }
        }
        .sheet(isPresented: $replying, onDismiss: { Task { await load() } }) { ComposeView(parent: postId) }
        .modifier(ModerationSheets(moderation: moderation))
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        do {
            posts = try await model.api.thread(postId: postId)
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }
}

/// Write a post or a reply. The server checks length, the word filter and plan limits.
struct ComposeView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let parent: String?
    @State private var text = ""
    @State private var shareSteps = false
    @State private var stepsToday: Int?
    @State private var busy = false
    @State private var error: String?
    @FocusState private var focused: Bool

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                TextEditor(text: $text)
                    .focused($focused)
                    .font(Typo.body)
                    .frame(minHeight: 160)
                    .padding(8)
                    .background(.white, in: RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Palette.line))
                    .onChange(of: text) { _, v in if v.count > 500 { text = String(v.prefix(500)) } }
                HStack {
                    Text("\(text.count)/500").font(Typo.small).foregroundStyle(Palette.muted)
                    Spacer()
                }
                if parent == nil, let stepsToday {
                    Toggle("Share today's \(formatSteps(stepsToday)) steps", isOn: $shareSteps).font(Typo.body)
                }
                if let error { NoticeText(text: error) }
                Text("Posts follow the community rules. Be kind.").font(Typo.small).foregroundStyle(Palette.muted)
                Spacer()
            }
            .padding(16)
            .pageBackground()
            .navigationTitle(parent == nil ? "New post" : "Reply")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(parent == nil ? "Post" : "Reply") { Task { await post() } }
                        .disabled(busy || (text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !shareSteps))
                }
            }
            .task {
                focused = true
                if parent == nil { stepsToday = (try? await model.api.myStepsToday())?.intValue }
            }
        }
    }

    private func post() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await model.api.createPost(body: text, parentId: parent, shareDay: shareSteps ? isoDay(Date()) : nil)
            await model.refreshPlan()
            dismiss()
        } catch {
            self.error = error.localizedDescription
        }
    }
}
