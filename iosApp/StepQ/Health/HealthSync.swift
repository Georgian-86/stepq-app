import Foundation
import HealthKit
import Observation
import Shared

/// One day of activity from Apple Health.
struct HealthDay: Equatable {
    let day: String
    var steps = 0
    var distanceM = 0
    var activeMinutes = 0
    var calories = 0
}

/// Reads daily totals from Apple Health (read only) and saves them to the person's StepQ account.
///
/// App Review 5.1.3 / HealthKit rules this follows:
/// - Only the four types StepQ shows are requested, and only to read.
/// - Data goes to StepQ's own backend for the features the purpose string names (progress,
///   streaks, friends, challenges). It's never used for ads, sold, or kept in iCloud.
/// - Nothing is read until the person taps "Connect Apple Health".
@MainActor
@Observable
final class HealthSync {
    private let store = HKHealthStore()
    private static let askedKey = "health.asked"

    /// Apple Health exists on this device (not on some iPads).
    let available = HKHealthStore.isHealthDataAvailable()
    /// The person went through the Apple Health permission sheet. iOS never tells apps whether
    /// read access was granted (that is private), so no data simply means 0 steps.
    private(set) var asked = UserDefaults.standard.bool(forKey: HealthSync.askedKey)
    private(set) var today = HealthDay(day: isoDay(Date()))
    private(set) var lastSync: Date?

    private let readTypes: Set<HKObjectType> = [
        HKQuantityType(.stepCount),
        HKQuantityType(.distanceWalkingRunning),
        HKQuantityType(.activeEnergyBurned),
        HKQuantityType(.appleExerciseTime),
    ]

    func requestAccess() async -> Bool {
        guard available else { return false }
        do {
            try await store.requestAuthorization(toShare: [], read: readTypes)
            asked = true
            UserDefaults.standard.set(true, forKey: Self.askedKey)
            return true
        } catch {
            return false
        }
    }

    /// Reads the last days and saves them, when the person has connected Apple Health.
    func syncIfConnected(api: StepqApi, days: Int = 30) async {
        guard available, asked, let me = api.myId() else { return }
        let read = await readDays(days)
        if let t = read.last, t.day == isoDay(Date()) { today = t }
        let rows = read.filter { $0.steps > 0 || $0.day == isoDay(Date()) }.map {
            api.newDay(userId: me, day: $0.day, steps: Int32($0.steps), distanceM: Int32($0.distanceM),
                       activeMinutes: Int32($0.activeMinutes), calories: Int32($0.calories))
        }
        do {
            try await api.saveDays(days: rows)
            lastSync = Date()
        } catch {
            // Offline: the next open saves them. Postgres upserts by (user, day).
        }
    }

    /// Forget the local state on log-out (Apple Health permissions stay with iOS).
    func forget() {
        today = HealthDay(day: isoDay(Date()))
        lastSync = nil
    }

    private func readDays(_ count: Int) async -> [HealthDay] {
        let cal = Calendar.current
        let end = Date()
        guard let start = cal.date(byAdding: .day, value: -(count - 1), to: cal.startOfDay(for: end)) else { return [] }
        async let steps = sums(.stepCount, .count(), start, end)
        async let distance = sums(.distanceWalkingRunning, .meter(), start, end)
        async let energy = sums(.activeEnergyBurned, .kilocalorie(), start, end)
        async let exercise = sums(.appleExerciseTime, .minute(), start, end)
        let (s, d, e, x) = await (steps, distance, energy, exercise)
        var out: [HealthDay] = []
        var day = start
        while day <= end {
            let key = isoDay(day)
            out.append(HealthDay(day: key, steps: Int(s[key] ?? 0), distanceM: Int(d[key] ?? 0),
                                 activeMinutes: Int(x[key] ?? 0), calories: Int(e[key] ?? 0)))
            guard let next = cal.date(byAdding: .day, value: 1, to: day) else { break }
            day = next
        }
        return out
    }

    /// Daily sums by "yyyy-MM-dd", with Apple Health's own de-duplication across iPhone and Watch.
    private func sums(_ id: HKQuantityTypeIdentifier, _ unit: HKUnit, _ start: Date, _ end: Date) async -> [String: Double] {
        let type = HKQuantityType(id)
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end)
        let descriptor = HKStatisticsCollectionQueryDescriptor(
            predicate: HKSamplePredicate.quantitySample(type: type, predicate: predicate),
            options: .cumulativeSum,
            anchorDate: Calendar.current.startOfDay(for: start),
            intervalComponents: DateComponents(day: 1)
        )
        guard let collection = try? await descriptor.result(for: store) else { return [:] }
        var out: [String: Double] = [:]
        collection.enumerateStatistics(from: start, to: end) { stats, _ in
            if let q = stats.sumQuantity() { out[isoDay(stats.startDate)] = q.doubleValue(for: unit) }
        }
        return out
    }
}
