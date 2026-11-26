import SwiftUI
import Shared

/// A13 Friends: requests, today's leaderboard, streaks and water reminders.
struct FriendsView: View {
    @Environment(AppModel.self) private var model
    @State private var data: FriendsData?
    @State private var streaks: [String: FriendStreak] = [:]
    @State private var error: String?
    @State private var notice: String?
    @State private var adding = false
    @State private var confirmRemove: PersonSummary?
    private let seeded: Bool

    init(seed: FriendsData? = nil) {
        _data = State(initialValue: seed)
        seeded = seed != nil
        if seed != nil { _streaks = State(initialValue: ["k": FriendStreak(friendId: "k", streak: 12, sharedToday: true)]) }
    }

    var body: some View {
        NavigationStack {
            Group {
                if let data {
                    list(data)
                } else if let error {
                    ErrorView(message: error) { Task { await load() } }
                } else {
                    LoadingView()
                }
            }
            .pageBackground()
            .navigationTitle("Friends")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { adding = true } label: { Label("Add friend", systemImage: "person.badge.plus") }
                }
            }
            .sheet(isPresented: $adding, onDismiss: { Task { await load() } }) { AddFriendView() }
            .confirmationDialog("Remove friend?", isPresented: Binding(get: { confirmRemove != nil }, set: { if !$0 { confirmRemove = nil } }), presenting: confirmRemove) { p in
                Button("Remove \(p.displayName)", role: .destructive) { Task { await act { try await model.api.removeFriend(otherId: p.id) } } }
            } message: { p in
                Text("Your streak with \(p.displayName) ends, and you'll stop seeing each other's steps.")
            }
            .task { await load() }
            .refreshable { await load() }
        }
    }

    private func list(_ d: FriendsData) -> some View {
        List {
            if let notice {
                Text(notice).font(Typo.small).foregroundStyle(Palette.deep)
            }
            if !d.requests.isEmpty {
                Section("Friend requests") {
                    ForEach(d.requests, id: \.id) { p in
                        HStack {
                            AvatarView(config: p.avatar, size: 40)
                            VStack(alignment: .leading) {
                                Text(p.displayName).font(Typo.bodyStrong)
                                if !p.username.isEmpty { Text("@\(p.username)").font(Typo.small).foregroundStyle(Palette.muted) }
                            }
                            Spacer()
                            Button("Accept") { Task { await act { try await model.api.acceptFriend(requesterId: p.id) } } }
                                .buttonStyle(.borderedProminent).tint(Palette.deep)
                            Button("Decline") { Task { await act { try await model.api.removeFriend(otherId: p.id) } } }
                                .buttonStyle(.bordered)
                        }
                    }
                }
            }
            Section {
                ForEach(Array(d.board.enumerated()), id: \.element.person.id) { i, row in
                    HStack(spacing: 12) {
                        Text("\(i + 1)").font(Typo.heading()).foregroundStyle(Palette.muted).frame(width: 24)
                        AvatarView(config: row.person.avatar, size: 40)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(row.isMe ? "You" : row.person.displayName).font(Typo.bodyStrong).foregroundStyle(Palette.ink)
                            Text("\(formatSteps(row.steps)) steps").font(Typo.small).foregroundStyle(Palette.muted)
                        }
                        Spacer()
                        if let s = streaks[row.person.id], s.streak > 0 {
                            Label("\(s.streak)", systemImage: "flame.fill").font(Typo.smallStrong).foregroundStyle(Palette.tomato)
                                .accessibilityLabel("\(s.streak) day streak")
                        }
                        if !row.isMe {
                            let reminded = d.remindedToday.contains(row.person.id)
                            let noneLeft = (d.remindersLeft?.intValue ?? 1) <= 0
                            Button {
                                Task { await act(success: "Water reminder sent to \(row.person.displayName).") { try await model.api.sendWaterReminder(toId: row.person.id) } }
                            } label: {
                                Image(systemName: reminded ? "drop.fill" : "drop")
                            }
                            .buttonStyle(.borderless)
                            .disabled(reminded || noneLeft)
                            .accessibilityLabel(reminded ? "Reminder sent today" : "Send \(row.person.displayName) a water reminder")
                        }
                    }
                    .swipeActions {
                        if !row.isMe {
                            Button("Remove", role: .destructive) { confirmRemove = row.person }
                        }
                    }
                }
            } header: {
                Text("Today")
            } footer: {
                if let left = d.remindersLeft {
                    Text("Water reminders left today: \(left.intValue)").font(Typo.small)
                } else {
                    Text("Unlimited water reminders with StepQ Pro.").font(Typo.small)
                }
            }
            if d.board.count <= 1 {
                Section {
                    Button { adding = true } label: { Label("Find friends on StepQ", systemImage: "magnifyingglass") }
                }
            }
        }
        .scrollContentBackground(.hidden)
    }

    private func load() async {
        if seeded { return }
        do {
            data = try await model.api.loadFriends()
            streaks = (try? await model.api.friendStreaks(today: isoDay(Date()))) ?? [:]
            error = nil
        } catch {
            if data == nil { self.error = error.localizedDescription }
        }
    }

    private func act(success: String? = nil, _ block: () async throws -> Void) async {
        do {
            try await block()
            notice = success
            await load()
        } catch {
            notice = error.localizedDescription
        }
    }
}

/// Find people by name or username, or from friends of friends.
struct AddFriendView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var results: [Suggestion] = []
    @State private var suggestions: [Suggestion] = []
    @State private var sent: Set<String> = []
    @State private var error: String?

    var body: some View {
        NavigationStack {
            List {
                if let error { Text(error).font(Typo.small).foregroundStyle(Palette.tomatoText) }
                if !query.isEmpty {
                    Section("Results") {
                        if results.isEmpty { Text("No one found yet. Try their exact @username.").font(Typo.small).foregroundStyle(Palette.muted) }
                        ForEach(results, id: \.id) { row($0) }
                    }
                }
                if !suggestions.isEmpty {
                    Section("People you may know") { ForEach(suggestions, id: \.id) { row($0) } }
                }
            }
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "Name or @username")
            .task(id: query) {
                try? await Task.sleep(for: .milliseconds(300))
                guard !Task.isCancelled else { return }
                results = (try? await model.api.searchPeople(query: query)) ?? []
            }
            .task { suggestions = (try? await model.api.peopleYouMayKnow()) ?? [] }
            .navigationTitle("Add friends")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Done") { dismiss() } } }
        }
    }

    private func row(_ s: Suggestion) -> some View {
        HStack(spacing: 12) {
            AvatarView(config: s.avatar, size: 40)
            VStack(alignment: .leading) {
                Text(s.displayName).font(Typo.bodyStrong)
                Text("@\(s.username)" + (s.mutual > 0 ? " · \(s.mutual) mutual" : "")).font(Typo.small).foregroundStyle(Palette.muted)
            }
            Spacer()
            if sent.contains(s.id) {
                Text("Sent").font(Typo.smallStrong).foregroundStyle(Palette.muted)
            } else {
                Button("Add") {
                    Task {
                        do {
                            try await model.api.sendFriendRequest(toId: s.id)
                            sent.insert(s.id)
                        } catch { self.error = error.localizedDescription }
                    }
                }
                .buttonStyle(.borderedProminent).tint(Palette.deep)
            }
        }
    }
}
