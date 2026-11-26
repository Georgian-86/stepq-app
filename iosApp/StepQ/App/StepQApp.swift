import SwiftUI
import Shared

@main
struct StepQApp: App {
    @State private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .environment(model.health)
                .environment(model.store)
                .tint(Palette.deep)
                // Email confirmation, password reset and web sign-in links: stepq://auth-callback?code=…
                .onOpenURL { url in model.open(url) }
        }
    }
}
