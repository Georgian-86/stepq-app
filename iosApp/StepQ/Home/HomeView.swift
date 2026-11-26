import SwiftUI
import Shared

/// A12 Home: today's ring, this week, StepQ score and friends today, with per-friend streaks.
struct HomeView: View {
    @Environment(AppModel.self) private var model
    @Environment(HealthSync.self) private var health
    @State private var data: HomeData?
    @State private var error: String?
    @State private var sharing = false
    @State private var notice: String?
    private let seeded: Bool

    init(seed: HomeData? = nil) {
        _data = State(initialValue: seed)
        seeded = seed != nil
    }

    var body: some View {
        NavigationStack {
            Group {
                if let data {
                    content(data)
                } else if let error {
                    ErrorView(message: error) { Task { await load() } }
                } else {
                    LoadingView()
                }
            }
            .pageBackground()
            .navigationTitle("Today")
            .task { await load() }
            .refreshable {
                await health.syncIfConnected(api: model.api, days: 7)
                await load()
            }
        }
    }

    private func content(_ d: HomeData) -> some View {
        let todayKey = isoDay(Date())
        let saved = d.myDays.first { $0.day == todayKey }.map { Int($0.steps) } ?? 0
        let steps = max(saved, health.today.day == todayKey ? health.today.steps : 0)
        let goal = Int(d.me.dailyGoal)
        return ScrollView {
            VStack(spacing: 16) {
                if health.available && !health.asked {
                    ConnectHealthBanner()
                }
                StepRing(steps: steps, goal: goal)
                    .card()
                WeekBars(days: d.myDays, goal: goal, todaySteps: steps)
                    .card()
                if let score = d.score {
                    HStack {
                        VStack(alignment: .leading) {
                            Text("StepQ score").font(Typo.smallStrong).foregroundStyle(Palette.muted)
                            Text("\(score.intValue)").font(Typo.display(32)).foregroundStyle(Palette.deep)
                        }
                        Spacer()
                        Text("One point for every day you reach your goal.").font(Typo.small).foregroundStyle(Palette.muted).frame(maxWidth: 170, alignment: .trailing)
                    }
                    .card()
                }
                friends(d, steps: steps)
            }
            .padding(16)
        }
    }

    @ViewBuilder
    private func friends(_ d: HomeData, steps: Int) -> some View {
        let streaks = d.streaks ?? [:]
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Friends today").font(Typo.heading()).foregroundStyle(Palette.ink)
                Spacer()
            }
            if d.friendsToday.isEmpty {
                Text("Add friends in the Friends tab to keep streaks together.").font(Typo.small).foregroundStyle(Palette.muted)
            } else {
                ForEach(d.friendsToday.sorted { ($0.second?.intValue ?? 0) > ($1.second?.intValue ?? 0) }, id: \.self) { pair in
                    if let p = pair.first {
                        HStack(spacing: 12) {
                            AvatarView(config: p.avatar, size: 40)
                            VStack(alignment: .leading) {
                                Text(p.displayName).font(Typo.bodyStrong).foregroundStyle(Palette.ink)
                                Text("\(formatSteps(Int(pair.second?.intValue ?? 0))) steps").font(Typo.small).foregroundStyle(Palette.muted)
                            }
                            Spacer()
                            if let s = streaks[p.id], s.streak > 0 {
                                Label("\(s.streak)", systemImage: "flame.fill").font(Typo.smallStrong).foregroundStyle(Palette.tomato)
                                    .accessibilityLabel("\(s.streak) day streak")
                            }
                        }
                    }
                }
                let waiting = d.friendsToday.compactMap { $0.first?.id }.filter { !(streaks[$0]?.sharedToday ?? false) }
                if let notice { NoticeText(text: notice, isError: false) }
                Button {
                    Task { await share(waiting, steps: steps) }
                } label: {
                    if sharing { ProgressView().tint(.white) } else { Label(waiting.isEmpty ? "Shared with everyone today" : "Share today's steps", systemImage: "flame.fill") }
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(sharing || waiting.isEmpty)
                Text("Sharing your steps with a friend each day keeps your streak with them going.").font(Typo.small).foregroundStyle(Palette.muted)
            }
        }
        .card()
    }

    private func load() async {
        if seeded { return }
        do {
            data = try await model.api.loadHome(today: isoDay(Date()))
            error = nil
        } catch {
            if data == nil { self.error = error.localizedDescription }
        }
    }

    private func share(_ ids: [String], steps: Int) async {
        sharing = true
        defer { sharing = false }
        do {
            let n = try await model.api.shareSteps(friendIds: ids, steps: Int32(steps), day: isoDay(Date())).intValue
            notice = n == 1 ? "Shared with 1 friend." : "Shared with \(n) friends."
            await load()
        } catch {
            notice = error.localizedDescription
        }
    }
}

struct ConnectHealthBanner: View {
    @Environment(AppModel.self) private var model
    @Environment(HealthSync.self) private var health
    @State private var showInfo = false

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "heart.fill").foregroundStyle(Palette.tomato)
            VStack(alignment: .leading, spacing: 2) {
                Text("Count your steps").font(Typo.bodyStrong).foregroundStyle(Palette.ink)
                Text("Connect Apple Health so Piko can fill your ring.").font(Typo.small).foregroundStyle(Palette.muted)
            }
            Spacer()
            Button("Connect") { showInfo = true }.font(Typo.bodyStrong)
        }
        .card()
        .sheet(isPresented: $showInfo) {
            NavigationStack {
                ConnectHealthStep { showInfo = false }
                    .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close") { showInfo = false } } }
            }
        }
    }
}

/// Today's progress ring.
struct StepRing: View {
    let steps: Int
    let goal: Int

    var body: some View {
        let progress = goal > 0 ? min(Double(steps) / Double(goal), 1) : 0
        VStack(spacing: 8) {
            ZStack {
                Circle().stroke(Palette.mint, lineWidth: 18)
                Circle().trim(from: 0, to: progress)
                    .stroke(Palette.leaf, style: StrokeStyle(lineWidth: 18, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .animation(.easeOut(duration: 0.6), value: progress)
                VStack(spacing: 2) {
                    Text(formatSteps(steps)).font(Typo.display(40)).foregroundStyle(Palette.ink)
                    Text("of \(formatSteps(goal)) steps").font(Typo.small).foregroundStyle(Palette.muted)
                }
            }
            .frame(width: 210, height: 210)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(formatSteps(steps)) of \(formatSteps(goal)) steps today")
            Text(steps >= goal ? "Goal reached. Piko is proud of you!" : "\(formatSteps(goal - steps)) to go")
                .font(Typo.bodyStrong).foregroundStyle(steps >= goal ? Palette.deep : Palette.muted)
        }
        .frame(maxWidth: .infinity)
    }
}

/// The last seven days as bars, with the goal line.
struct WeekBars: View {
    let days: [DailySteps]
    let goal: Int
    let todaySteps: Int

    var body: some View {
        let cal = Calendar.current
        let today = Date()
        let keys = (0..<7).reversed().compactMap { cal.date(byAdding: .day, value: -$0, to: today) }
        let byDay = Dictionary(days.map { ($0.day, Int($0.steps)) }, uniquingKeysWith: { a, _ in a })
        let values = keys.map { d -> (String, Int) in
            let key = isoDay(d)
            let v = key == isoDay(today) ? max(todaySteps, byDay[key] ?? 0) : (byDay[key] ?? 0)
            return (d.formatted(.dateTime.weekday(.narrow)), v)
        }
        let top = max(goal, values.map(\.1).max() ?? 0, 1)
        VStack(alignment: .leading, spacing: 10) {
            Text("This week").font(Typo.heading()).foregroundStyle(Palette.ink)
            HStack(alignment: .bottom, spacing: 10) {
                ForEach(values.indices, id: \.self) { i in
                    VStack(spacing: 6) {
                        RoundedRectangle(cornerRadius: 6)
                            .fill(values[i].1 >= goal ? Palette.leaf : Palette.leafSoft)
                            .frame(height: max(6, 110 * CGFloat(values[i].1) / CGFloat(top)))
                        Text(values[i].0).font(Typo.small).foregroundStyle(Palette.muted)
                    }
                    .frame(maxWidth: .infinity)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(values[i].0): \(formatSteps(values[i].1)) steps")
                }
            }
            .frame(height: 140, alignment: .bottom)
        }
    }
}
