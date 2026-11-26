import SwiftUI
import Shared

/// A26 Profile and settings.
struct ProfileView: View {
    @Environment(AppModel.self) private var model
    @Environment(HealthSync.self) private var health
    @State private var score: Int?
    @State private var editing = false
    @State private var goalSheet = false
    @State private var confirmLogOut = false

    var body: some View {
        NavigationStack {
            List {
                if let me = model.profile {
                    Section {
                        HStack(spacing: 14) {
                            AvatarView(config: me.avatar, size: 64)
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text(me.displayName).font(Typo.title(22))
                                    if model.isPro { ProBadge() }
                                }
                                Text("@\(me.username)").font(Typo.small).foregroundStyle(Palette.muted)
                                if let score { Text("StepQ score \(score)").font(Typo.smallStrong).foregroundStyle(Palette.deep) }
                            }
                            Spacer()
                            Button("Edit") { editing = true }.font(Typo.bodyStrong)
                        }
                    }
                    Section {
                        Button { goalSheet = true } label: {
                            row("target", "Daily goal", value: "\(formatSteps(me.dailyGoal)) steps" + (me.nextDailyGoal.map { " → \(formatSteps($0.intValue)) tomorrow" } ?? ""))
                        }
                        NavigationLink { ProHubView() } label: {
                            row("crown.fill", "StepQ Pro", value: model.isPro ? "Member" : "Free")
                        }
                        NavigationLink { HealthSettingsView() } label: {
                            row("heart.fill", "Apple Health", value: health.asked ? "Connected" : "Not connected")
                        }
                    }
                }
                Section {
                    NavigationLink { PrivacyDataView() } label: { row("lock.shield", "Privacy and data") }
                    NavigationLink { HelpView() } label: { row("questionmark.circle", "Help and contact") }
                }
                Section {
                    Button("Log out") { confirmLogOut = true }.foregroundStyle(Palette.tomatoText)
                }
                Section {
                    NavigationLink { DeleteAccountView() } label: {
                        Text("Delete account").foregroundStyle(Palette.tomatoText)
                    }
                } footer: {
                    Text("StepQ \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "")")
                }
            }
            .navigationTitle("Profile")
            .sheet(isPresented: $editing, onDismiss: { Task { await model.refreshProfile() } }) { EditProfileView() }
            .sheet(isPresented: $goalSheet, onDismiss: { Task { await model.refreshProfile() } }) { GoalSheet() }
            .confirmationDialog("Log out of StepQ?", isPresented: $confirmLogOut) {
                Button("Log out", role: .destructive) { Task { await model.logOut() } }
            }
            .task {
                await model.refreshProfile()
                score = (try? await model.api.myScore())?.intValue
            }
        }
    }

    private func row(_ icon: String, _ title: String, value: String? = nil) -> some View {
        HStack {
            Label(title, systemImage: icon).font(Typo.body).foregroundStyle(Palette.ink)
            Spacer()
            if let value { Text(value).font(Typo.small).foregroundStyle(Palette.muted) }
        }
    }
}

/// Change the daily goal. It applies from tomorrow, so today's streak is judged fairly.
struct GoalSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var goal = 8000
    @State private var error: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text("\(formatSteps(goal)) steps").font(Typo.display(40)).foregroundStyle(Palette.deep)
                Stepper("Daily goal", value: $goal, in: 1000...100000, step: 500)
                Text("Your new goal starts tomorrow. Today's goal stays the same.").font(Typo.small).foregroundStyle(Palette.muted)
                if let error { NoticeText(text: error) }
                Spacer()
            }
            .padding(24)
            .navigationTitle("Daily goal")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        Task {
                            do {
                                try await model.api.updateGoal(goal: Int32(goal), current: model.profile?.dailyGoal ?? 8000)
                                dismiss()
                            } catch { self.error = error.localizedDescription }
                        }
                    }
                }
            }
            .onAppear { goal = Int(model.profile?.nextDailyGoal?.int32Value ?? model.profile?.dailyGoal ?? 8000) }
        }
    }
}

struct EditProfileView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var draft = OnboardingDraft()
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HStack { Spacer(); AvatarView(config: draft.avatar, size: 110); Spacer() }
                    AvatarPicker(draft: draft)
                    LabeledField(label: "Your name", text: $draft.fullName, content: .name).textInputAutocapitalization(.words)
                    LabeledField(label: "Username", text: $draft.username, content: .username)
                    if let error { NoticeText(text: error) }
                }
                .padding(24)
            }
            .pageBackground()
            .navigationTitle("Edit profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { Task { await save() } }.disabled(busy)
                }
            }
            .onAppear(perform: fill)
        }
    }

    private func fill() {
        guard let me = model.profile else { return }
        draft.fullName = me.displayName
        draft.username = me.username
        draft.gender = me.gender
        draft.skin = Int(me.avatar.skin); draft.hair = Int(me.avatar.hair); draft.outfit = Int(me.avatar.outfit)
        draft.headband = Int(me.avatar.headband); draft.style = Int(me.avatar.style)
    }

    private func save() async {
        busy = true
        defer { busy = false }
        let username = model.api.normalizeUsername(input: draft.username)
        guard model.api.isValidUsername(username: username) else {
            error = "Usernames are 3–20 letters, numbers, dots or underscores."
            return
        }
        do {
            if username != model.profile?.username {
                let available = try await model.api.usernameAvailable(username: username).boolValue
                if !available {
                    error = "@\(username) is taken."
                    return
                }
            }
            try await model.api.updateProfile(displayName: draft.fullName, username: username, avatar: draft.avatar, gender: draft.gender)
            dismiss()
        } catch {
            self.error = error.localizedDescription
        }
    }
}

struct HealthSettingsView: View {
    @Environment(AppModel.self) private var model
    @Environment(HealthSync.self) private var health
    @State private var syncing = false

    var body: some View {
        List {
            Section { HealthExplainer() }
            Section {
                if !health.available {
                    Text("Apple Health isn't available on this device.")
                } else if health.asked {
                    Button(syncing ? "Syncing…" : "Sync now") {
                        Task {
                            syncing = true
                            await health.syncIfConnected(api: model.api, days: 30)
                            syncing = false
                        }
                    }
                    .disabled(syncing)
                    if let last = health.lastSync {
                        Text("Last synced \(last.formatted(.relative(presentation: .named)))").font(Typo.small).foregroundStyle(Palette.muted)
                    }
                    Text("To stop StepQ reading Apple Health, open the Health app → Sharing → Apps → StepQ, and turn the data off.").font(Typo.small).foregroundStyle(Palette.muted)
                } else {
                    Button("Connect Apple Health") {
                        Task {
                            if await health.requestAccess() {
                                try? await model.api.recordConsent(purpose: "health", version: Legal.policyVersion, granted: true)
                                await health.syncIfConnected(api: model.api)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("Apple Health")
    }
}
