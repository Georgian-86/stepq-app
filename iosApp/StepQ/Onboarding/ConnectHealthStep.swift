import SwiftUI
import Shared

/// A10 "Let Piko count your steps": explains exactly what is read and why, before iOS asks.
struct ConnectHealthStep: View {
    @Environment(AppModel.self) private var model
    @Environment(HealthSync.self) private var health
    let next: () -> Void
    @State private var busy = false

    var body: some View {
        StepPage(title: "Let Piko count your steps") {
            HStack { Spacer(); Piko(pose: .body, size: 120); Spacer() }
            HealthExplainer()
            if !health.available {
                NoticeText(text: "Apple Health isn't available on this device, so StepQ can't count steps here. You can still use StepQ with friends.", isError: false)
            }
        } footer: {
            VStack(spacing: 10) {
                if health.available {
                    Button {
                        Task {
                            busy = true
                            defer { busy = false }
                            if await health.requestAccess() {
                                try? await model.api.recordConsent(purpose: "health", version: Legal.policyVersion, granted: true)
                                await health.syncIfConnected(api: model.api)
                            }
                            next()
                        }
                    } label: {
                        if busy { ProgressView().tint(.white) } else { Label("Connect Apple Health", systemImage: "heart.fill") }
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    .disabled(busy)
                }
                Button("Not now", action: next).buttonStyle(SecondaryButtonStyle())
            }
        }
        .navigationBarBackButtonHidden()
    }
}

/// What StepQ reads from Apple Health and what it does with it. Shown before the iOS sheet and
/// in Profile → Privacy and data.
struct HealthExplainer: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            row("figure.walk", "Reads", "Steps, walking distance, active energy and exercise minutes. Nothing else, and StepQ never writes to Apple Health.")
            row("person.2.fill", "Shares", "Your daily totals are saved to your StepQ account and shown to the friends you add, in your groups and in challenge rankings.")
            row("hand.raised.fill", "Never", "Used for advertising, sold, or given to anyone else.")
            row("switch.2", "Your choice", "Turn access off any time in the Health app → Sharing → Apps → StepQ.")
            Link("How StepQ handles health data", destination: Legal.healthData).font(Typo.bodyStrong)
        }
    }

    private func row(_ icon: String, _ title: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon).foregroundStyle(Palette.deep).frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(Typo.bodyStrong).foregroundStyle(Palette.ink)
                Text(text).font(Typo.small).foregroundStyle(Palette.muted)
            }
        }
    }
}
