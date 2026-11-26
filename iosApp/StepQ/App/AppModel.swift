import Foundation
import Observation
import Shared

/// Where a session lands, the same rules as Android's StepqNavHost.
enum Landing: Equatable {
    case loading
    case notConfigured
    case signedOut
    case onboarding
    case parentConsent
    case reconsent
    case main
}

/// App-wide state: the shared Kotlin API, the signed-in person, their plan and where they are.
@MainActor
@Observable
final class AppModel {
    let api: StepqApi
    let health = HealthSync()
    let store: ProStore

    private(set) var landing: Landing = .loading
    private(set) var profile: Profile?
    private(set) var plan: MyPlan?
    /// A password reset link was opened: ask for a new password before anything else.
    private(set) var recoveringPassword = false
    /// A message to show once, e.g. after an email link was opened.
    var banner: String?

    @ObservationIgnored private var watches: [Watch] = []

    init() {
        let api = StepqApi(supabaseUrl: AppConfig.supabaseURL, anonKey: AppConfig.supabaseAnonKey)
        self.api = api
        self.store = ProStore(api: api)
        #if DEBUG
        if Demo.screen != nil {
            profile = Demo.me
            plan = MyPlan.Companion.shared.FREE_DEFAULTS
            landing = .main
            return
        }
        #endif
        guard api.isConfigured else {
            landing = .notConfigured
            return
        }
        watches.append(api.watchAuth { [weak self] state in
            Task { @MainActor in await self?.authChanged(state) }
        })
        watches.append(api.watchPasswordRecovery { [weak self] active in
            Task { @MainActor in self?.recoveringPassword = active.boolValue }
        })
        store.onPlanChanged = { [weak self] in await self?.refreshPlan() }
    }

    var myId: String? { api.myId() }
    var isPro: Bool { plan?.isPro ?? false }

    private func authChanged(_ state: String) async {
        switch state {
        case "signedOut":
            profile = nil
            plan = nil
            landing = .signedOut
        case "signedIn":
            await route()
        default:
            if landing == .signedOut || landing == .notConfigured { return }
            landing = .loading
        }
    }

    /// Works out where a signed-in person belongs. Offline or before a migration: let them in.
    func route() async {
        do {
            let me = try await api.myProfile()
            profile = me
            guard me != nil else {
                landing = .onboarding
                return
            }
            let parent = (try? await api.parentalConsent()) ?? "not_needed"
            if parent != "granted" && parent != "not_needed" {
                landing = .parentConsent
                return
            }
            if let agreed = try? await api.hasConsent(version: Legal.policyVersion, purpose: "terms_privacy"),
               agreed.boolValue == false {
                landing = .reconsent
                return
            }
            landing = .main
            await refreshPlan()
            await health.syncIfConnected(api: api)
            await store.start()
        } catch {
            landing = .main
        }
    }

    func refreshProfile() async {
        if let me = try? await api.myProfile() { profile = me }
    }

    func refreshPlan() async {
        plan = try? await api.myPlan()
    }

    func open(_ url: URL) {
        guard url.scheme == "stepq" else { return }
        Task {
            do {
                if try await api.openAuthLink(url: url.absoluteString).boolValue {
                    banner = "You're signed in."
                }
            } catch {
                banner = error.localizedDescription
            }
        }
    }

    func logOut() async {
        await store.stop()
        try? await api.logOut()
        health.forget()
    }
}
