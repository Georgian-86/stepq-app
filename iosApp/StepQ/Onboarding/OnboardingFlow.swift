import SwiftUI
import Shared

/// What a new account fills in before its profile is saved (A06–A11).
@Observable
final class OnboardingDraft {
    /// The name Sign in with Apple shared the first time, so it's never asked for again.
    nonisolated(unsafe) static var appleName: String?

    var fullName = ""
    var username = ""
    var birthDigits = ""
    var birthdate: String?
    var gender: String?
    var skin = 2, hair = 0, outfit = 0, headband = 0, style = 0
    var goal = 8000
    var agreed = false

    var avatar: AvatarConfig { makeAvatar(skin: skin, hair: hair, outfit: outfit, headband: headband, style: style) }
}

enum OnboardingStep: Hashable { case about, avatar, goal, health, done }

struct OnboardingFlow: View {
    @Environment(AppModel.self) private var model
    @State private var draft = OnboardingDraft()
    @State private var path: [OnboardingStep] = []
    @State private var deleting = false

    var body: some View {
        NavigationStack(path: $path) {
            NameStep(draft: draft) { path.append(.about) }
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Menu {
                            Button("Log out") { Task { await model.logOut() } }
                            Button("Delete account", role: .destructive) { deleting = true }
                        } label: {
                            Image(systemName: "ellipsis.circle")
                        }
                        .accessibilityLabel("Account")
                    }
                }
                .navigationDestination(for: OnboardingStep.self) { step in
                    switch step {
                    case .about: AboutYouStep(draft: draft) { path.append(.avatar) }
                    case .avatar: AvatarStep(draft: draft) { path.append(.goal) }
                    case .goal: GoalStep(draft: draft) { created in
                        if created { path.append(.health) }
                    }
                    case .health: ConnectHealthStep { path.append(.done) }
                    case .done: AllSetStep { Task { await model.route() } }
                    }
                }
        }
        .sheet(isPresented: $deleting) { NavigationStack { DeleteAccountView() } }
        .onAppear {
            if draft.fullName.isEmpty {
                draft.fullName = OnboardingDraft.appleName ?? model.api.fullNameHint() ?? ""
            }
        }
    }
}

/// A06 Name and username.
private struct NameStep: View {
    @Environment(AppModel.self) private var model
    @Bindable var draft: OnboardingDraft
    let next: () -> Void
    @State private var checking = false
    @State private var error: String?

    var body: some View {
        StepPage(title: "What should we call you?", step: 1) {
            LabeledField(label: "Your name", text: $draft.fullName, prompt: "Riya Sharma", content: .name)
                .textInputAutocapitalization(.words)
            LabeledField(label: "Username", text: $draft.username, prompt: "riya.walks", content: .username)
            let suggestions = model.api.suggestUsernames(fullName: draft.fullName)
            if !suggestions.isEmpty && draft.username.isEmpty {
                HStack {
                    Text("Or pick one:").font(Typo.small).foregroundStyle(Palette.muted)
                    ForEach(suggestions, id: \.self) { s in
                        Button("@\(s)") { draft.username = s }.font(Typo.smallStrong).foregroundStyle(Palette.deep)
                    }
                }
            }
            Text("3–20 letters, numbers, dots or underscores. Friends find you by it.").font(Typo.small).foregroundStyle(Palette.muted)
            if let error { NoticeText(text: error) }
        } footer: {
            Button {
                Task { await check() }
            } label: {
                if checking { ProgressView().tint(.white) } else { Text("Continue") }
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(checking || draft.fullName.trimmingCharacters(in: .whitespaces).isEmpty || !model.api.isValidUsername(username: model.api.normalizeUsername(input: draft.username)))
        }
    }

    private func check() async {
        checking = true
        defer { checking = false }
        draft.username = model.api.normalizeUsername(input: draft.username)
        do {
            if try await model.api.usernameAvailable(username: draft.username).boolValue {
                error = nil
                next()
            } else {
                error = "@\(draft.username) is taken. Try another."
            }
        } catch {
            self.error = error.localizedDescription
        }
    }
}

/// A07 About you: birthdate (13+, under 18 needs a parent) and gender.
private struct AboutYouStep: View {
    @Environment(AppModel.self) private var model
    @Bindable var draft: OnboardingDraft
    let next: () -> Void
    private let genders: [(String, String)] = [("female", "Female"), ("male", "Male"), ("non_binary", "Non-binary"), ("prefer_not", "Prefer not to say")]

    var body: some View {
        StepPage(title: "A bit about you", step: 2) {
            LabeledField(label: "Birthdate (DD MM YYYY)", text: $draft.birthDigits, prompt: "14032001", keyboard: .numberPad, content: .birthdate)
                .onChange(of: draft.birthDigits) { _, v in
                    let digits = String(v.filter(\.isNumber).prefix(8))
                    if digits != v { draft.birthDigits = digits }
                    draft.birthdate = model.api.parseBirthdate(digits: digits)
                }
            if draft.birthDigits.count == 8 && draft.birthdate == nil {
                NoticeText(text: "Check the date. You need to be 13 or older to use StepQ.")
            } else if model.api.isMinor(birthdateIso: draft.birthdate) {
                NoticeText(text: "You're under 18, so a parent or guardian will need to agree before you start. We'll ask for their email next.", isError: false)
            }
            Text("We use your age to keep StepQ safe for teens. It's never shown to others.").font(Typo.small).foregroundStyle(Palette.muted)
            Text("Gender (optional)").font(Typo.smallStrong).foregroundStyle(Palette.muted)
            ForEach(genders, id: \.0) { g in
                Button {
                    draft.gender = g.0
                    draft.style = Int(model.api.defaultHairStyle(gender: g.0))
                } label: {
                    HStack {
                        Text(g.1).font(Typo.body).foregroundStyle(Palette.ink)
                        Spacer()
                        Image(systemName: draft.gender == g.0 ? "checkmark.circle.fill" : "circle").foregroundStyle(Palette.deep)
                    }
                    .card(padding: 14)
                }
            }
        } footer: {
            Button("Continue", action: next).buttonStyle(PrimaryButtonStyle()).disabled(draft.birthdate == nil)
        }
    }
}

/// A08 Avatar builder.
private struct AvatarStep: View {
    @Bindable var draft: OnboardingDraft
    let next: () -> Void

    var body: some View {
        StepPage(title: "Make your avatar", step: 3) {
            HStack { Spacer(); AvatarView(config: draft.avatar, size: 140); Spacer() }
            AvatarPicker(draft: draft)
        } footer: {
            Button("Continue", action: next).buttonStyle(PrimaryButtonStyle())
        }
    }
}

struct AvatarPicker: View {
    @Bindable var draft: OnboardingDraft

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Picker("Style", selection: $draft.style) {
                ForEach(AvatarPalette.styles.indices, id: \.self) { Text(AvatarPalette.styles[$0]).tag($0) }
            }
            .pickerStyle(.segmented)
            swatches("Skin", AvatarPalette.skin, $draft.skin)
            swatches("Hair", AvatarPalette.hair, $draft.hair)
            swatches("Outfit", AvatarPalette.outfit, $draft.outfit)
            swatches("Headband", AvatarPalette.headband, $draft.headband)
        }
    }

    private func swatches(_ label: String, _ colors: [Color], _ value: Binding<Int>) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(Typo.smallStrong).foregroundStyle(Palette.muted)
            HStack(spacing: 12) {
                ForEach(colors.indices, id: \.self) { i in
                    Button {
                        value.wrappedValue = i
                    } label: {
                        Circle().fill(colors[i]).frame(width: 36, height: 36)
                            .overlay(Circle().strokeBorder(value.wrappedValue == i ? Palette.ink : Palette.line, lineWidth: value.wrappedValue == i ? 3 : 1))
                    }
                    .accessibilityLabel("\(label) \(i + 1)")
                    .accessibilityAddTraits(value.wrappedValue == i ? .isSelected : [])
                }
            }
        }
    }
}

/// A09 Daily goal, the Terms agreement, then the profile is saved.
private struct GoalStep: View {
    @Environment(AppModel.self) private var model
    @Bindable var draft: OnboardingDraft
    let done: (Bool) -> Void
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        StepPage(title: "Pick a daily goal", step: 4) {
            Text("\(formatSteps(draft.goal)) steps").font(Typo.display(44)).foregroundStyle(Palette.deep)
            Text(String(format: "About %.1f km a day. You can change it any time.", Double(draft.goal) * 0.000762))
                .font(Typo.small).foregroundStyle(Palette.muted)
            HStack {
                ForEach([5000, 8000, 10000, 12000], id: \.self) { g in
                    Button("\(g / 1000)k") { draft.goal = g }
                        .font(Typo.bodyStrong)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(draft.goal == g ? Palette.leafSoft : .white, in: Capsule())
                        .overlay(Capsule().strokeBorder(draft.goal == g ? Palette.deep : Palette.line))
                        .foregroundStyle(Palette.ink)
                }
            }
            Stepper("Fine-tune", value: $draft.goal, in: 1000...100000, step: 500).font(Typo.body)
            HStack(alignment: .top, spacing: 10) {
                Button {
                    draft.agreed.toggle()
                } label: {
                    Image(systemName: draft.agreed ? "checkmark.square.fill" : "square")
                        .font(.title3).foregroundStyle(Palette.deep)
                }
                .accessibilityLabel("I agree to the Terms of use and Privacy policy")
                .accessibilityAddTraits(draft.agreed ? .isSelected : [])
                LegalLine(before: "I agree to StepQ's ", after: ", including how my step data is used and shared with friends.")
            }
            if let error { NoticeText(text: error) }
        } footer: {
            Button {
                Task { await save() }
            } label: {
                if busy { ProgressView().tint(.white) } else { Text("Save and continue") }
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(busy || !draft.agreed)
        }
    }

    private func save() async {
        busy = true
        defer { busy = false }
        do {
            try await model.api.createProfile(
                displayName: draft.fullName, username: draft.username, gender: draft.gender,
                birthdate: draft.birthdate, avatar: draft.avatar, dailyGoal: Int32(draft.goal)
            )
            // Evidence of consent (DPDP s.6): best effort, the profile is already saved.
            try? await model.api.recordConsent(purpose: "terms_privacy", version: Legal.policyVersion, granted: true)
            if model.api.isMinor(birthdateIso: draft.birthdate) {
                await model.route() // goes to the parent consent page
                done(false)
            } else {
                done(true)
            }
        } catch {
            self.error = error.localizedDescription
        }
    }
}

/// A11 All set.
private struct AllSetStep: View {
    let finish: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Spacer()
            Piko(pose: .cheer, size: 160)
            Text("You're all set!").font(Typo.title(30)).foregroundStyle(Palette.ink)
            Text("Add friends to start streaks, and walk to fill today's ring.").font(Typo.body).foregroundStyle(Palette.muted).multilineTextAlignment(.center)
            Spacer()
            Button("Let's go", action: finish).buttonStyle(PrimaryButtonStyle())
        }
        .padding(24)
        .pageBackground()
        .navigationBarBackButtonHidden()
    }
}

/// A form page with a title, a step count and a pinned button.
struct StepPage<Content: View, Footer: View>: View {
    let title: String
    var step: Int? = nil
    @ViewBuilder var content: Content
    @ViewBuilder var footer: Footer

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let step { Text("Step \(step) of 4").font(Typo.smallStrong).foregroundStyle(Palette.deep) }
                    Text(title).font(Typo.title()).foregroundStyle(Palette.ink)
                    content
                }
                .padding(24)
            }
            footer.padding(.horizontal, 24).padding(.bottom, 12)
        }
        .pageBackground()
    }
}

struct CheckboxStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        Button {
            configuration.isOn.toggle()
        } label: {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: configuration.isOn ? "checkmark.square.fill" : "square")
                    .font(.title3).foregroundStyle(Palette.deep)
                configuration.label.multilineTextAlignment(.leading)
            }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(configuration.isOn ? .isSelected : [])
    }
}
