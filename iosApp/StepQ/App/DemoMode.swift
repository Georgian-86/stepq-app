#if DEBUG
import SwiftUI
import Shared

/// Debug builds only: `-demoScreen home` (etc.) opens one screen with sample data and no
/// network, for CI screenshots and App Store screenshots. Not compiled into release builds.
enum Demo {
    static var screen: String? {
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: "-demoScreen"), i + 1 < args.count else { return nil }
        return args[i + 1]
    }

    static let me = Profile(
        id: "me", displayName: "Riya Sharma", username: "riya.walks", gender: "female", birthdate: "2001-03-14",
        avatar: makeAvatar(skin: 1, hair: 0, outfit: 0, headband: 0, style: 1), dailyGoal: 8000,
        nextDailyGoal: nil, nextGoalFrom: nil, plan: "free", country: "IN"
    )

    static func person(_ id: String, _ name: String, _ user: String, _ skin: Int, _ style: Int, _ outfit: Int) -> PersonSummary {
        PersonSummary(id: id, displayName: name, username: user,
                      avatar: makeAvatar(skin: skin, hair: skin % 3, outfit: outfit, headband: (skin + 1) % 5, style: style),
                      dailyGoal: 8000)
    }

    static let kabir = person("k", "Kabir Mehta", "kabir", 2, 0, 3)
    static let ananya = person("a", "Ananya Iyer", "ananya.i", 3, 2, 1)
    static let dev = person("d", "Dev Patel", "devwalks", 1, 0, 2)

    static var days: [DailySteps] {
        let steps = [9120, 6400, 11250, 8030, 7310, 10400, 6240]
        return steps.enumerated().map { i, s in
            let d = Calendar.current.date(byAdding: .day, value: -i, to: Date())!
            return DailySteps(userId: "me", day: isoDay(d), steps: Int32(s), distanceM: Int32(Double(s) * 0.762), activeMinutes: 40, calories: 280)
        }
    }

    static var home: HomeData {
        HomeData(
            me: me, myDays: days,
            friendsToday: [KotlinPair(first: kabir, second: KotlinInt(int: 9840)), KotlinPair(first: ananya, second: KotlinInt(int: 5210)), KotlinPair(first: dev, second: KotlinInt(int: 12030))],
            streaks: ["k": FriendStreak(friendId: "k", streak: 12, sharedToday: true), "a": FriendStreak(friendId: "a", streak: 4, sharedToday: false)],
            score: KotlinInt(int: 47)
        )
    }

    static var friends: FriendsData {
        let mePerson = PersonSummary(id: "me", displayName: me.displayName, username: me.username, avatar: me.avatar, dailyGoal: 8000)
        return FriendsData(
            me: me,
            board: [
                FriendToday(person: dev, steps: 12030, distanceM: 9100, isMe: false, score: nil),
                FriendToday(person: kabir, steps: 9840, distanceM: 7400, isMe: false, score: nil),
                FriendToday(person: mePerson, steps: 6240, distanceM: 4750, isMe: true, score: nil),
                FriendToday(person: ananya, steps: 5210, distanceM: 3900, isMe: false, score: nil),
            ],
            requests: [person("s", "Sana Khan", "sana.k", 0, 1, 4)],
            sentRequestIds: [], remindedToday: ["a"], remindersLeft: KotlinInt(int: 2), reminderLimit: KotlinInt(int: 3)
        )
    }

    static func post(_ id: String, _ p: PersonSummary, _ body: String, minutesAgo: Double, likes: Int32, replies: Int32, steps: Int32? = nil) -> Post {
        let at = ISO8601DateFormatter().string(from: Date().addingTimeInterval(-minutesAgo * 60))
        return Post(
            id: id, authorId: p.id, authorName: p.displayName, authorUsername: p.username, authorAvatar: p.avatar,
            parentId: nil, parentUsername: nil, body: body, kind: steps == nil ? "thought" : "achievement",
            day: nil, steps: steps.map { KotlinInt(int: $0) }, goal: steps == nil ? nil : KotlinInt(int: 8000),
            createdAt: at, likeCount: likes, replyCount: replies, repostCount: 1, liked: id == "2", reposted: false,
            entryAt: at, repostedBy: nil, depth: 0, pending: false
        )
    }

    static var posts: [Post] {
        [
            post("1", kabir, "Morning walk by the lake before work. 5 km done and the chai tasted better for it ☕️", minutesAgo: 12, likes: 24, replies: 3),
            post("2", dev, "", minutesAgo: 40, likes: 51, replies: 8, steps: 12030),
            post("3", ananya, "Anyone up for an evening walk challenge this week? Let's push each other past 10k!", minutesAgo: 95, likes: 17, replies: 5),
        ]
    }

    static var challenges: ChallengesData {
        let end = ISO8601DateFormatter().string(from: Date().addingTimeInterval(9 * 86400))
        let start = ISO8601DateFormatter().string(from: Date().addingTimeInterval(-5 * 86400))
        let c1 = Challenge(id: "c1", title: "Monsoon 100K", partnerName: "StepQ", description: "Walk 100,000 steps before the month ends.",
                           reward: "A StepQ T-shirt for everyone who finishes", targetSteps: KotlinInt(int: 100000),
                           startsAt: start, endsAt: end, proOnly: false)
        let c2 = Challenge(id: "c2", title: "Sunrise Streak", partnerName: nil, description: "Walk 5,000 steps before 8 am, five days in a row.",
                           reward: nil, targetSteps: KotlinInt(int: 25000), startsAt: start, endsAt: end, proOnly: true)
        let mePerson = PersonSummary(id: "me", displayName: me.displayName, username: me.username, avatar: me.avatar, dailyGoal: 8000)
        let ranks = [Ranked(person: dev, steps: 71200, isMe: false, rank: 1), Ranked(person: kabir, steps: 64010, isMe: false, rank: 2),
                     Ranked(person: mePerson, steps: 58750, isMe: true, rank: 3), Ranked(person: ananya, steps: 41900, isMe: false, rank: 4)]
        return ChallengesData(me: me, weekly: ranks, monthly: ranks, country: "IN", weeklyCount: 1240, monthlyCount: 1240,
                              challenges: [ChallengeCard(challenge: c1, joined: 1240, myJoinedAt: nil, myProgress: 0),
                                           ChallengeCard(challenge: c2, joined: 312, myJoinedAt: nil, myProgress: 0)])
    }
}

/// The screen a demo launch shows.
struct DemoRoot: View {
    let screen: String

    var body: some View {
        switch screen {
        case "welcome": WelcomeView()
        case "home": NavigationStack { HomeView(seed: Demo.home) }
        case "friends": FriendsView(seed: Demo.friends)
        case "community": NavigationStack { FeedView(seed: Demo.posts).navigationTitle("Community").pageBackground() }
        case "rules": NavigationStack { CommunityRulesView {}.navigationTitle("Community").pageBackground() }
        case "challenges": ChallengesView(seed: Demo.challenges)
        case "profile": ProfileView()
        case "paywall": PaywallView(reason: "Starting posts is for StepQ Pro members. You can still like and reply.")
        case "delete": NavigationStack { DeleteAccountView() }
        case "health": NavigationStack { ConnectHealthStep {} }
        default: MainTabView()
        }
    }
}
#endif
