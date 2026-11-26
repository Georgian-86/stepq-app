import SwiftUI
import Shared

/// A22–A25: weekly and monthly rankings, and challenges to join.
///
/// Challenges with rewards are contests run by StepQ (App Review Guideline 5.3.2): each shows the
/// official rules and says Apple isn't involved. Winners are decided by steps walked, never by
/// chance, and joining is free (Pro-only challenges come with the subscription, not a fee).
struct ChallengesView: View {
    @Environment(AppModel.self) private var model
    @State private var data: ChallengesData?
    @State private var error: String?
    @State private var monthly = false
    @State private var notice: String?
    @State private var showUpgrade = false
    private let seeded: Bool

    init(seed: ChallengesData? = nil) {
        _data = State(initialValue: seed)
        seeded = seed != nil
    }

    var body: some View {
        NavigationStack {
            Group {
                if let data { content(data) } else if let error {
                    ErrorView(message: error) { Task { await load() } }
                } else { LoadingView() }
            }
            .pageBackground()
            .navigationTitle("Challenges")
            .task { await load() }
            .refreshable { await load() }
            .sheet(isPresented: $showUpgrade) { PaywallView(reason: "This challenge is for StepQ Pro members.") }
        }
    }

    private func content(_ d: ChallengesData) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if let notice { NoticeText(text: notice, isError: false) }
                if !d.challenges.isEmpty {
                    Text("Join a challenge").font(Typo.heading()).foregroundStyle(Palette.ink)
                    ForEach(d.challenges, id: \.challenge.id) { card in challengeCard(card) }
                    ContestDisclaimer()
                }
                Picker("Ranking", selection: $monthly) {
                    Text("This week").tag(false)
                    Text("This month").tag(true)
                }
                .pickerStyle(.segmented)
                let rows = monthly ? d.monthly : d.weekly
                let count = monthly ? d.monthlyCount : d.weeklyCount
                Text(scopeLabel(d.country, Int(count))).font(Typo.small).foregroundStyle(Palette.muted)
                VStack(spacing: 10) {
                    ForEach(rows, id: \.person.id) { r in
                        HStack(spacing: 12) {
                            Text("\(r.rank)").font(Typo.heading()).foregroundStyle(r.rank <= 3 ? Palette.deep : Palette.muted).frame(width: 32)
                            AvatarView(config: r.person.avatar, size: 36)
                            Text(r.isMe ? "You" : r.person.displayName).font(r.isMe ? Typo.bodyStrong : Typo.body).foregroundStyle(Palette.ink)
                            Spacer()
                            Text(formatSteps(r.steps)).font(Typo.bodyStrong).foregroundStyle(Palette.ink)
                        }
                    }
                    if rows.isEmpty { Text("No steps yet this period. Be the first!").font(Typo.small).foregroundStyle(Palette.muted) }
                }
                .card()
            }
            .padding(16)
        }
    }

    private func challengeCard(_ c: ChallengeCard) -> some View {
        let ch = c.challenge
        let joined = c.myJoinedAt != nil
        let ends = parseTimestamp(ch.endsAt)
        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(ch.title).font(Typo.heading()).foregroundStyle(Palette.ink)
                if ch.proOnly { ProBadge() }
                Spacer()
            }
            if let partner = ch.partnerName { Text("With \(partner)").font(Typo.small).foregroundStyle(Palette.muted) }
            if let about = ch.description_ { Text(about).font(Typo.body).foregroundStyle(Palette.ink) }
            if let reward = ch.reward { Label(reward, systemImage: "gift.fill").font(Typo.bodyStrong).foregroundStyle(Palette.deep) }
            if let target = ch.targetSteps {
                let progress = min(Double(c.myProgress) / Double(max(target.intValue, 1)), 1)
                if joined {
                    ProgressView(value: progress).tint(Palette.leaf)
                    Text("\(formatSteps(c.myProgress)) of \(formatSteps(target.intValue)) steps").font(Typo.small).foregroundStyle(Palette.muted)
                } else {
                    Text("Walk \(formatSteps(target.intValue)) steps").font(Typo.small).foregroundStyle(Palette.muted)
                }
            }
            HStack {
                if let ends { Text("Ends \(ends.formatted(.dateTime.day().month()))").font(Typo.small).foregroundStyle(Palette.muted) }
                Text("· \(c.joined) joined").font(Typo.small).foregroundStyle(Palette.muted)
                Spacer()
                if joined {
                    Button("Leave") { Task { await act { try await model.api.leaveChallenge(challengeId: ch.id) } } }
                        .buttonStyle(.bordered)
                } else {
                    Button("Join") {
                        if ch.proOnly && !(model.plan?.has(key: "all_challenges") ?? false) {
                            showUpgrade = true
                        } else {
                            Task { await act(success: "You joined \(ch.title).") { try await model.api.joinChallenge(challengeId: ch.id) } }
                        }
                    }
                    .buttonStyle(.borderedProminent).tint(Palette.deep)
                }
            }
        }
        .card()
    }

    private func scopeLabel(_ country: String?, _ count: Int) -> String {
        let name = country.flatMap { Locale.current.localizedString(forRegionCode: $0) } ?? "your country"
        return "Everyone in \(name) · \(count.formatted()) \(count == 1 ? "walker" : "walkers")"
    }

    private func load() async {
        if seeded { return }
        do {
            data = try await model.api.loadChallenges(country: Locale.current.region?.identifier)
            error = nil
        } catch {
            if data == nil { self.error = error.localizedDescription }
        }
    }

    private func act(success: String? = nil, _ block: () async throws -> Void) async {
        do {
            try await block()
            notice = success
            await load()
        } catch {
            notice = error.localizedDescription
        }
    }
}

/// Required for contests on the App Store (5.3.2): official rules, and Apple isn't a sponsor.
struct ContestDisclaimer: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Challenges and rewards are run by StepQ with the partners named. Apple is not a sponsor of, and is not involved in, any StepQ challenge or reward. There is no entry fee, and winners are decided by steps walked, never by chance.")
                .font(Typo.small).foregroundStyle(Palette.muted)
            Link("Official rules", destination: Legal.challengeRules).font(Typo.smallStrong)
        }
    }
}
