import SwiftUI
import Shared

/// Privacy and data: what StepQ keeps, a copy of it, blocked people, and the policies.
struct PrivacyDataView: View {
    @Environment(AppModel.self) private var model
    @State private var exportURL: URL?
    @State private var exporting = false
    @State private var error: String?

    var body: some View {
        List {
            Section {
                Button(exporting ? "Preparing…" : "Download my data") { Task { await export() } }
                    .disabled(exporting)
                if let exportURL {
                    ShareLink("Save or share my data", item: exportURL)
                }
                if let error { Text(error).font(Typo.small).foregroundStyle(Palette.tomatoText) }
            } footer: {
                Text("A copy of everything StepQ holds about you, as a JSON file.")
            }
            Section {
                NavigationLink("Blocked people") { BlockedPeopleView() }
            }
            Section("Policies") {
                Link("Privacy policy", destination: Legal.privacy)
                Link("How StepQ handles health data", destination: Legal.healthData)
                Link("Terms of use", destination: Legal.terms)
                Link("Community rules", destination: Legal.communityRules)
            }
            Section {
                Text("StepQ doesn't track you across other apps or websites, doesn't show ads and doesn't sell your data. Your steps are shared only with your friends, your groups and in challenge rankings.")
                    .font(Typo.small).foregroundStyle(Palette.muted)
            }
        }
        .navigationTitle("Privacy and data")
    }

    private func export() async {
        exporting = true
        defer { exporting = false }
        do {
            let json = try await model.api.exportMyData()
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("stepq-my-data.json")
            try Data(json.utf8).write(to: url, options: .completeFileProtection)
            exportURL = url
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }
}

struct BlockedPeopleView: View {
    @Environment(AppModel.self) private var model
    @State private var people: [BlockedUser] = []
    @State private var loaded = false

    var body: some View {
        List {
            if loaded && people.isEmpty { Text("You haven't blocked anyone.").foregroundStyle(Palette.muted) }
            ForEach(people, id: \.userId) { p in
                HStack {
                    VStack(alignment: .leading) {
                        Text(p.displayName).font(Typo.bodyStrong)
                        Text("@\(p.username)").font(Typo.small).foregroundStyle(Palette.muted)
                    }
                    Spacer()
                    Button("Unblock") {
                        Task {
                            try? await model.api.unblock(userId: p.userId)
                            await load()
                        }
                    }
                    .buttonStyle(.bordered)
                }
            }
        }
        .navigationTitle("Blocked people")
        .task { await load() }
    }

    private func load() async {
        people = (try? await model.api.myBlocks()) ?? []
        loaded = true
    }
}

struct HelpView: View {
    var body: some View {
        List {
            Section {
                Link("Email StepQ support", destination: URL(string: "mailto:\(Legal.supportEmail)")!)
                Link("Report a problem or a grievance", destination: Legal.grievance)
                Link("stepq.in", destination: Legal.site)
            } footer: {
                Text("We answer within 2 working days, and act on reports of abusive content within 24 hours.")
            }
        }
        .navigationTitle("Help and contact")
    }
}

/// Deleting the account inside the app (Guideline 5.1.1(v)). Everything goes: profile, steps,
/// friends, posts, photos and consents, through app.delete_my_account(). For Sign in with Apple
/// accounts StepQ's Apple tokens are revoked first, as Apple requires.
struct DeleteAccountView: View {
    @Environment(AppModel.self) private var model
    @Environment(ProStore.self) private var store
    @State private var confirm = false
    @State private var busy = false
    @State private var error: String?
    @State private var manage = false
    @State private var apple = AppleSignIn()

    var body: some View {
        List {
            Section {
                Text("Deleting your account removes your profile, steps, streaks, friends, posts and photos from StepQ. It can't be undone.")
                if model.isPro {
                    Text("Deleting your account doesn't cancel an App Store subscription. Cancel StepQ Pro first so you're not charged again.")
                        .foregroundStyle(Palette.tomatoText)
                    Button("Manage subscription") { manage = true }
                }
                if model.api.usesApple() {
                    Text("You signed in with Apple, so Apple will ask you to confirm. This also disconnects StepQ from your Apple ID.")
                        .font(Typo.small).foregroundStyle(Palette.muted)
                }
                Text("Apple Health keeps its own copy of your steps; StepQ never deletes those.").font(Typo.small).foregroundStyle(Palette.muted)
            }
            if let error { Section { Text(error).foregroundStyle(Palette.tomatoText) } }
            Section {
                Button(busy ? "Deleting…" : "Delete my account", role: .destructive) { confirm = true }
                    .disabled(busy)
            }
        }
        .navigationTitle("Delete account")
        .manageSubscriptionsSheet(isPresented: $manage)
        .confirmationDialog("Delete your StepQ account for good?", isPresented: $confirm, titleVisibility: .visible) {
            Button("Delete my account", role: .destructive) { Task { await delete() } }
        }
    }

    private func delete() async {
        busy = true
        defer { busy = false }
        do {
            var code: String?
            if model.api.usesApple() {
                do {
                    code = try await apple.run().authorizationCode
                } catch AppleSignInError.cancelled {
                    return
                }
            }
            try await model.api.deleteAccount(appleAuthorizationCode: code)
            await store.stop()
            model.health.forget()
        } catch {
            self.error = error.localizedDescription
        }
    }
}
