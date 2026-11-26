import AuthenticationServices
import SwiftUI
import Shared

/// A02 Log in: Sign in with Apple, Google (when switched on), and email + password.
struct WelcomeView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.webAuthenticationSession) private var webAuth
    @State private var providers = AuthProviders(apple: true)
    @State private var email = ""
    @State private var password = ""
    @State private var busy = false
    @State private var error: String?
    @State private var showSignUp = false
    @State private var showForgot = false
    @State private var appleNonce = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    Piko(pose: .cheer, size: 130).padding(.top, 24)
                    Text("Walk together, every day").font(Typo.title(28)).foregroundStyle(Palette.ink).multilineTextAlignment(.center)
                    Text("Count your steps, keep streaks with friends and join challenges.")
                        .font(Typo.body).foregroundStyle(Palette.muted).multilineTextAlignment(.center)

                    if providers.apple {
                        SignInWithAppleButton(.continue) { request in
                            appleNonce = Nonce.make()
                            request.requestedScopes = [.fullName, .email]
                            request.nonce = Nonce.sha256(appleNonce)
                        } onCompletion: { result in
                            handleApple(result)
                        }
                        .signInWithAppleButtonStyle(.black)
                        .frame(height: 52)
                        .clipShape(Capsule())
                        .disabled(busy)
                    }
                    if providers.google {
                        Button {
                            Task { await google() }
                        } label: {
                            Label("Continue with Google", systemImage: "globe")
                        }
                        .buttonStyle(SecondaryButtonStyle())
                        .disabled(busy)
                    }

                    if providers.apple || providers.google {
                        HStack {
                            Rectangle().fill(Palette.line).frame(height: 1)
                            Text("or with email").font(Typo.small).foregroundStyle(Palette.muted)
                            Rectangle().fill(Palette.line).frame(height: 1)
                        }
                    }

                    LabeledField(label: "Email", text: $email, prompt: "you@example.com", keyboard: .emailAddress, content: .username)
                    LabeledField(label: "Password", text: $password, secure: true, content: .password)
                    if let error { NoticeText(text: error) }
                    Button {
                        Task { await logIn() }
                    } label: {
                        if busy { ProgressView().tint(.white) } else { Text("Log in") }
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    .disabled(busy || email.isEmpty || password.isEmpty)

                    Button("Forgot password?") { showForgot = true }
                        .font(Typo.bodyStrong).foregroundStyle(Palette.deep)
                    Button {
                        showSignUp = true
                    } label: {
                        Text("New to StepQ? ").foregroundStyle(Palette.muted) + Text("Create an account").foregroundStyle(Palette.deep).bold()
                    }
                    .font(Typo.body)
                    LegalLine().multilineTextAlignment(.center).padding(.top, 8)
                }
                .padding(24)
            }
            .pageBackground()
            .navigationDestination(isPresented: $showSignUp) { SignUpView() }
            .sheet(isPresented: $showForgot) { ForgotPasswordView(email: email) }
            .task { providers = await AuthProviders.load() }
        }
    }

    private func logIn() async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            try await model.api.logIn(email: email, password: password)
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func handleApple(_ result: Result<ASAuthorization, Error>) {
        switch result {
        case .failure(let e):
            if (e as? ASAuthorizationError)?.code != .canceled { error = "Apple couldn't sign you in. Try again." }
        case .success(let auth):
            Task {
                busy = true
                error = nil
                defer { busy = false }
                do {
                    let cred = try AppleSignIn.credential(from: auth, rawNonce: appleNonce)
                    // Apple shares the name only the first time; keep it to fill in the profile,
                    // so we never ask for it again.
                    if let name = cred.fullName { OnboardingDraft.appleName = name }
                    try await model.api.logInWithApple(idToken: cred.idToken, rawNonce: cred.rawNonce)
                } catch {
                    self.error = error.localizedDescription
                }
            }
        }
    }

    private func google() async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            let start = try await model.api.googleSignInUrl()
            guard let url = URL(string: start) else { return }
            let callback = try await webAuth.authenticate(using: url, callbackURLScheme: "stepq", preferredBrowserSession: .ephemeral)
            _ = try await model.api.openAuthLink(url: callback.absoluteString)
        } catch let e as ASWebAuthenticationSessionError where e.code == .canceledLogin {
            // Closed the sheet: nothing to say.
        } catch {
            self.error = error.localizedDescription
        }
    }
}

/// A05 Sign up with email and password.
struct SignUpView: View {
    @Environment(AppModel.self) private var model
    @State private var email = ""
    @State private var password = ""
    @State private var busy = false
    @State private var error: String?
    @State private var sentTo: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Create your account").font(Typo.title()).foregroundStyle(Palette.ink)
                LabeledField(label: "Email", text: $email, prompt: "you@example.com", keyboard: .emailAddress, content: .username)
                LabeledField(label: "Password", text: $password, prompt: "8+ characters with a number", secure: true, content: .newPassword)
                if let error { NoticeText(text: error) }
                if let sentTo {
                    NoticeText(text: "We sent a link to \(sentTo). Open it on this iPhone to confirm your email, then log in.", isError: false)
                }
                Button {
                    Task { await signUp() }
                } label: {
                    if busy { ProgressView().tint(.white) } else { Text("Create account") }
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(busy || !model.api.isValidEmail(email: email) || !model.api.isValidPassword(password: password))
                Text("You must be 13 or older to use StepQ. If you're under 18, a parent or guardian needs to agree before you can start.")
                    .font(Typo.small).foregroundStyle(Palette.muted)
                LegalLine(before: "By creating an account you agree to StepQ's ")
            }
            .padding(24)
        }
        .pageBackground()
        .navigationTitle("Sign up")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func signUp() async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            let signedIn = try await model.api.signUp(email: email, password: password).boolValue
            if !signedIn { sentTo = email.trimmingCharacters(in: .whitespaces) }
        } catch {
            self.error = error.localizedDescription
        }
    }
}

/// A03 Forgot password: the emailed link opens StepQ, which then asks for a new password.
struct ForgotPasswordView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State var email: String
    @State private var sent = false
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text("We'll email you a link to set a new password.").font(Typo.body).foregroundStyle(Palette.muted)
                LabeledField(label: "Email", text: $email, keyboard: .emailAddress, content: .username)
                if let error { NoticeText(text: error) }
                if sent { NoticeText(text: "Check your email for the reset link and open it on this iPhone.", isError: false) }
                Button(sent ? "Send again" : "Send reset link") {
                    Task {
                        busy = true
                        defer { busy = false }
                        do {
                            try await model.api.sendPasswordReset(email: email)
                            sent = true
                            error = nil
                        } catch { self.error = error.localizedDescription }
                    }
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(busy || !model.api.isValidEmail(email: email))
                Spacer()
            }
            .padding(24)
            .pageBackground()
            .navigationTitle("Reset password")
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } } }
        }
    }
}

/// Shown after a password reset link signs the person in.
struct SetNewPasswordView: View {
    @Environment(AppModel.self) private var model
    @State private var password = ""
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                LabeledField(label: "New password", text: $password, prompt: "8+ characters with a number", secure: true, content: .newPassword)
                if let error { NoticeText(text: error) }
                Button("Save password") {
                    Task {
                        busy = true
                        defer { busy = false }
                        do { try await model.api.updatePassword(password: password) } catch { self.error = error.localizedDescription }
                    }
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(busy || !model.api.isValidPassword(password: password))
                Spacer()
            }
            .padding(24)
            .pageBackground()
            .navigationTitle("Set a new password")
        }
    }
}
