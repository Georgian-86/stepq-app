import SwiftUI
import Shared

struct MainTabView: View {
    @Environment(AppModel.self) private var model
    @Environment(HealthSync.self) private var health
    @Environment(\.scenePhase) private var phase
    @State private var tab = 0

    var body: some View {
        TabView(selection: $tab) {
            HomeView().tabItem { Label("Home", systemImage: "house.fill") }.tag(0)
            FriendsView().tabItem { Label("Friends", systemImage: "person.2.fill") }.tag(1)
            CommunityView().tabItem { Label("Community", systemImage: "bubble.left.and.bubble.right.fill") }.tag(2)
            ChallengesView().tabItem { Label("Challenges", systemImage: "trophy.fill") }.tag(3)
            ProfileView().tabItem { Label("Profile", systemImage: "person.crop.circle") }.tag(4)
        }
        .onChange(of: phase) { _, now in
            if now == .active { Task { await health.syncIfConnected(api: model.api, days: 3) } }
        }
    }
}
