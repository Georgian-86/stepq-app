import SwiftUI
import Shared

/// A26 Profile and settings.
struct ProfileView: View {
    @Environment(AppModel.self) private var model
    @Environment(HealthSync.self) private var health
    @State private var score: Int?
    @State private var editing = false
    @State private var goalSheet = false
    @State private var goalSaved: GoalChange?
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
            .sheet(isPresented: $goalSheet, onDismiss: { Task { await model.refreshProfile() } }) { GoalSheet(saved: $goalSaved) }
            .confirmationDialog("Log out of StepQ?", isPresented: $confirmLogOut) {
                Button("Log out", role: .destructive) { Task { await model.logOut() } }
            }
            .task {
                await model.refreshProfile()
                score = (try? await model.api.myScore())?.intValue
            }
        }
        .overlay {
            if let change = goalSaved, !goalSheet {
                GoalUpdatedPopup(copy: model.api.goalUpdatedCopy(change: change), change: change) {
                    withAnimation(.easeOut(duration: 0.2)) { goalSaved = nil }
                }
                .transition(.opacity)
            }
        }
        .animation(.easeOut(duration: 0.2), value: goalSaved == nil)
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
    /// Set to what was saved, so Profile can show the "Step goal updated" popup.
    @Binding var saved: GoalChange?
    @State private var goal = 8000
    @State private var error: String?
    @State private var busy = false

    private var today: Int { Int(model.profile?.dailyGoal ?? 8000) }

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text("\(formatSteps(goal)) steps").font(Typo.display(40)).foregroundStyle(Palette.deep)
                Stepper("Daily goal", value: $goal, in: 1000...100000, step: 500)
                Text(goal == today
                     ? "Today's goal is \(formatSteps(today)) steps."
                     : "Starts tomorrow. Today you're still aiming for \(formatSteps(today)) steps, so today counts at that goal.")
                    .font(Typo.small).foregroundStyle(Palette.muted).multilineTextAlignment(.center)
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
                        busy = true
                        Task {
                            do {
                                let change = try await model.api.updateGoal(goal: Int32(goal), current: Int32(today))
                                saved = change
                                dismiss()
                            } catch { self.error = error.localizedDescription }
                            busy = false
                        }
                    }
                    .disabled(busy || goal == Int(model.profile?.nextDailyGoal?.int32Value ?? Int32(today)))
                }
            }
            .onAppear { goal = Int(model.profile?.nextDailyGoal?.int32Value ?? Int32(today)) }
        }
    }
}

/// Shown after a goal change is saved: the new goal starts tomorrow, today's stays.
struct GoalUpdatedPopup: View {
    let copy: GoalUpdatedCopy
    let change: GoalChange
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea().onTapGesture(perform: onDismiss)
            VStack(spacing: 0) {
                ZStack {
                    Circle().fill(Palette.mint).frame(width: 72, height: 72)
                    Circle().fill(Palette.leafSoft).frame(width: 52, height: 52)
                    Image(systemName: change.nextDailyGoal != nil ? "calendar.badge.checkmark" : "target")
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundStyle(Palette.deep)
                }
                .accessibilityHidden(true)
                Text(copy.title)
                    .font(Typo.title(22)).foregroundStyle(Palette.ink)
                    .multilineTextAlignment(.center)
                    .padding(.top, 16)
                    .accessibilityAddTraits(.isHeader)
                Text(copy.body)
                    .font(Typo.body).foregroundStyle(Palette.muted)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 8)
                if let next = change.nextDailyGoal {
                    HStack(spacing: 8) {
                        tile("TODAY", Int(change.dailyGoal), highlighted: false)
                        Image(systemName: "arrow.right").font(.footnote.weight(.semibold)).foregroundStyle(Palette.muted)
                            .accessibilityHidden(true)
                        tile((copy.startsLabel ?? "Tomorrow").uppercased(), Int(next.int32Value), highlighted: true)
                    }
                    .padding(.top, 20)
                }
                Button("Got it", action: onDismiss)
                    .buttonStyle(PrimaryButtonStyle())
                    .padding(.top, 24)
            }
            .padding(EdgeInsets(top: 28, leading: 24, bottom: 20, trailing: 24))
            .frame(maxWidth: 360)
            .background(.white, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
            .shadow(color: .black.opacity(0.12), radius: 24, y: 8)
            .padding(24)
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.isModal)
        }
    }

    private func tile(_ label: String, _ steps: Int, highlighted: Bool) -> some View {
        VStack(spacing: 2) {
            Text(label).font(Typo.smallStrong).foregroundStyle(highlighted ? Palette.deep : Palette.muted)
            Text(formatSteps(steps)).font(Typo.heading(20)).foregroundStyle(highlighted ? Palette.deep : Palette.ink)
            Text("steps").font(Typo.small).foregroundStyle(Palette.muted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .background(highlighted ? Palette.mint : Palette.page, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
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
