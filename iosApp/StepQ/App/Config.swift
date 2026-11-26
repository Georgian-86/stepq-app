import Foundation

/// Build settings the app reads at runtime (from Info.plist, filled from Config/*.xcconfig).
enum AppConfig {
    static var supabaseURL: String {
        let host = (Bundle.main.object(forInfoDictionaryKey: "SupabaseHost") as? String) ?? ""
        return host.isEmpty ? "" : "https://\(host)"
    }

    /// The public anon key. Row-level security decides what each user can read and write.
    static var supabaseAnonKey: String {
        (Bundle.main.object(forInfoDictionaryKey: "SupabaseAnonKey") as? String) ?? ""
    }
}

/// StepQ's legal pages and contacts: the same ones the Android app and stepq.in use.
enum Legal {
    static let site = URL(string: "https://stepq.in")!
    static let privacy = URL(string: "https://stepq.in/privacy")!
    static let healthData = URL(string: "https://stepq.in/privacy#health-and-step-data")!
    static let terms = URL(string: "https://stepq.in/terms")!
    static let communityRules = URL(string: "https://stepq.in/terms#community-rules")!
    static let challengeRules = URL(string: "https://stepq.in/terms#challenges-and-rewards")!
    static let grievance = URL(string: "https://stepq.in/grievance")!
    /// Apple's standard Terms of Use (EULA) for auto-renewable subscriptions.
    static let appleEULA = URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")!
    static let supportEmail = "connect@stepq.in"

    /// Bump together with the Android app when the Terms or Privacy policy change.
    static let policyVersion = "2026-11-24"
    /// The community rules a person agrees to before posting or reading the feed (Apple 1.2).
    static let communityRulesVersion = "2026-11-26"
}
