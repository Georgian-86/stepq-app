import AuthenticationServices
import CryptoKit
import Foundation

/// Which sign-in providers are switched on in Supabase (GET /auth/v1/settings, public).
/// Google is only offered next to Sign in with Apple (App Review Guideline 4.8).
struct AuthProviders: Equatable {
    var apple = false
    var google = false

    static func load() async -> AuthProviders {
        guard let url = URL(string: AppConfig.supabaseURL + "/auth/v1/settings") else { return AuthProviders() }
        var request = URLRequest(url: url)
        request.setValue(AppConfig.supabaseAnonKey, forHTTPHeaderField: "apikey")
        guard let (data, _) = try? await URLSession.shared.data(for: request),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let external = json["external"] as? [String: Any] else {
            // Can't tell: offer Apple (it's on the device) and leave Google out.
            return AuthProviders(apple: true, google: false)
        }
        let apple = (external["apple"] as? Bool) ?? false
        let google = (external["google"] as? Bool) ?? false
        return AuthProviders(apple: apple, google: google && apple)
    }
}

/// A one-time random nonce for Sign in with Apple: Apple gets its SHA-256, Supabase the raw value.
enum Nonce {
    static func make(length: Int = 32) -> String {
        let chars = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var bytes = [UInt8](repeating: 0, count: length)
        _ = SecRandomCopyBytes(kSecRandomDefault, length, &bytes)
        return String(bytes.map { chars[Int($0) % chars.count] })
    }

    static func sha256(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}

/// What a Sign in with Apple sheet returned.
struct AppleCredential {
    let idToken: String
    let rawNonce: String
    /// Short-lived; only used when deleting the account, to revoke StepQ's Apple tokens.
    let authorizationCode: String?
    let fullName: String?
}

enum AppleSignInError: LocalizedError {
    case cancelled
    case failed(String)

    var errorDescription: String? {
        switch self {
        case .cancelled: return nil
        case .failed(let m): return m
        }
    }
}

/// Runs Sign in with Apple outside of a button, e.g. to re-confirm before deleting the account.
@MainActor
final class AppleSignIn: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private var continuation: CheckedContinuation<AppleCredential, Error>?
    private var nonce = ""

    func run(scopes: [ASAuthorization.Scope] = []) async throws -> AppleCredential {
        nonce = Nonce.make()
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = scopes
        request.nonce = Nonce.sha256(nonce)
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        return try await withCheckedThrowingContinuation { c in
            continuation = c
            controller.performRequests()
        }
    }

    nonisolated func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        MainActor.assumeIsolated {
            UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first ?? ASPresentationAnchor()
        }
    }

    nonisolated func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        MainActor.assumeIsolated {
            continuation?.resume(with: Result { try AppleSignIn.credential(from: authorization, rawNonce: nonce) })
            continuation = nil
        }
    }

    nonisolated func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        MainActor.assumeIsolated {
            let cancelled = (error as? ASAuthorizationError)?.code == .canceled
            continuation?.resume(throwing: cancelled ? AppleSignInError.cancelled : AppleSignInError.failed("Apple couldn't sign you in. Try again."))
            continuation = nil
        }
    }

    static func credential(from authorization: ASAuthorization, rawNonce: String) throws -> AppleCredential {
        guard let cred = authorization.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = cred.identityToken,
              let token = String(data: tokenData, encoding: .utf8) else {
            throw AppleSignInError.failed("Apple didn't return a sign-in token. Try again.")
        }
        let code = cred.authorizationCode.flatMap { String(data: $0, encoding: .utf8) }
        let name = cred.fullName.map { PersonNameComponentsFormatter().string(from: $0) }?.trimmingCharacters(in: .whitespaces)
        return AppleCredential(idToken: token, rawNonce: rawNonce, authorizationCode: code, fullName: name?.isEmpty == true ? nil : name)
    }
}
