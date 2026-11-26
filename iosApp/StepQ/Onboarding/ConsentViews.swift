import SwiftUI
import Shared

/// A07b For 13 to 17 year olds: a parent or guardian agrees on stepq.in before the app opens
/// (India's DPDP Act s.9). The email goes through stepq.in, which checks the session.
struct ParentConsentView: View {
    @Environment(AppModel.self) private var model
    @State private var status = "none"
    @State private var parentName = ""
    @State private var parentEmail = ""
    @State private var busy = false
    @State private var error: String?
    @State private var deleting = false

    var body: some View {
        StepPage(title: status == "pending" ? "Waiting for your parent" : "Ask a parent to agree") {
            HStack { Spacer(); Piko(pose: .nudge, size: 110); Spacer() }
            switch status {
            case "pending":
                Text("We emailed your parent or guardian a link. Once they agree, StepQ opens for you.").font(Typo.body).foregroundStyle(Palette.muted)
            case "declined":
                NoticeText(text: "Your parent or guardian said no, so StepQ can't be used on this account. You can ask them again or delete the account from Profile.")
            default:
                Text("You're under 18, so a parent or guardian needs to agree before you can use StepQ. We'll email them a link.").font(Typo.body).foregroundStyle(Palette.muted)
            }
            LabeledField(label: "Parent's name", text: $parentName, content: .name).textInputAutocapitalization(.words)
            LabeledField(label: "Parent's email", text: $parentEmail, keyboard: .emailAddress, content: .emailAddress)
            if let error { NoticeText(text: error) }
        } footer: {
            VStack(spacing: 10) {
                Button(status == "pending" ? "Send the email again" : "Email my parent") {
                    Task { await send() }
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(busy || parentName.isEmpty || !model.api.isValidEmail(email: parentEmail))
                Button("I've been approved, check again") { Task { await model.route() } }
                    .buttonStyle(SecondaryButtonStyle())
                HStack(spacing: 24) {
                    Button("Log out") { Task { await model.logOut() } }
                    Button("Delete account") { deleting = true }
                }
                .font(Typo.bodyStrong).foregroundStyle(Palette.muted)
            }
        }
        .sheet(isPresented: $deleting) { NavigationStack { DeleteAccountView() } }
        .task { status = (try? await model.api.parentalConsent()) ?? "none" }
    }

    private func send() async {
        busy = true
        defer { busy = false }
        do {
            try await model.api.requestParentalConsent(parentName: parentName, parentEmail: parentEmail)
            status = "pending"
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }
}

/// The Terms or Privacy policy changed (or the account predates the consent log): agree again.
struct ReconsentView: View {
    @Environment(AppModel.self) private var model
    @State private var agreed = false
    @State private var busy = false
    @State private var deleting = false

    var body: some View {
        StepPage(title: "We've updated our terms") {
            Text("Please read and agree to the updated Terms of use and Privacy policy to keep using StepQ.")
                .font(Typo.body).foregroundStyle(Palette.muted)
            HStack(alignment: .top, spacing: 10) {
                Button { agreed.toggle() } label: {
                    Image(systemName: agreed ? "checkmark.square.fill" : "square").font(.title3).foregroundStyle(Palette.deep)
                }
                .accessibilityLabel("I agree")
                LegalLine(before: "I agree to StepQ's ")
            }
        } footer: {
            VStack(spacing: 10) {
                Button("Continue") {
                    Task {
                        busy = true
                        defer { busy = false }
                        try? await model.api.recordConsent(purpose: "terms_privacy", version: Legal.policyVersion, granted: true)
                        await model.route()
                    }
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(!agreed || busy)
                HStack(spacing: 24) {
                    Button("Log out") { Task { await model.logOut() } }
                    Button("Delete account") { deleting = true }
                }
                .font(Typo.bodyStrong).foregroundStyle(Palette.muted)
            }
        }
        .sheet(isPresented: $deleting) { NavigationStack { DeleteAccountView() } }
    }
}
