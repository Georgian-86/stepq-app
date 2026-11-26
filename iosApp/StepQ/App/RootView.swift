import SwiftUI
import Shared

struct RootView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        Group {
            #if DEBUG
            if let screen = Demo.screen {
                DemoRoot(screen: screen)
            } else {
                landingView
            }
            #else
            landingView
            #endif
        }
        .animation(.easeInOut(duration: 0.25), value: model.landing)
        .sheet(isPresented: Binding(get: { model.recoveringPassword }, set: { _ in })) {
            SetNewPasswordView()
                .interactiveDismissDisabled()
        }
        .alert("StepQ", isPresented: Binding(get: { model.banner != nil }, set: { if !$0 { model.banner = nil } })) {
            Button("OK", role: .cancel) { model.banner = nil }
        } message: {
            Text(model.banner ?? "")
        }
    }

    @ViewBuilder
    private var landingView: some View {
        Group {
            switch model.landing {
            case .loading:
                SplashView()
            case .notConfigured:
                NotConfiguredView()
            case .signedOut:
                WelcomeView()
            case .onboarding:
                OnboardingFlow()
            case .parentConsent:
                ParentConsentView()
            case .reconsent:
                ReconsentView()
            case .main:
                MainTabView()
            }
        }
    }
}

struct SplashView: View {
    var body: some View {
        VStack(spacing: 16) {
            Piko(pose: .happy, size: 140)
            Text("StepQ").font(Typo.display(40)).foregroundStyle(.white)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Palette.deep.ignoresSafeArea())
    }
}

/// A build without the Supabase key (see iosApp/README.md). Never shown in a release build
/// made the documented way.
struct NotConfiguredView: View {
    var body: some View {
        VStack(spacing: 16) {
            Piko(pose: .nudge)
            Text("This build isn't connected to StepQ yet.").font(Typo.heading()).multilineTextAlignment(.center)
            Text("Add SUPABASE_ANON_KEY to iosApp/Config/Secrets.xcconfig and build again.")
                .font(Typo.small).foregroundStyle(Palette.muted).multilineTextAlignment(.center)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .pageBackground()
    }
}
