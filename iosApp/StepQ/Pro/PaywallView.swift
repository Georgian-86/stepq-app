import StoreKit
import SwiftUI
import Shared

/// "Upgrade to StepQ Pro". Uses Apple's SubscriptionStoreView, which shows each plan's price,
/// length and renewal terms the way Guideline 3.1.2 asks, plus Restore Purchases and links to the
/// Terms of Use and Privacy Policy.
struct PaywallView: View {
    @Environment(AppModel.self) private var model
    @Environment(ProStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    var reason: String?

    var body: some View {
        SubscriptionStoreView(productIDs: ProStore.productIDs) {
            ProMarketing(plan: model.plan, reason: reason)
        }
        .subscriptionStoreControlStyle(.prominentPicker)
        .subscriptionStoreButtonLabel(.multiline)
        .storeButton(.visible, for: .restorePurchases)
        .storeButton(.visible, for: .cancellation)
        .subscriptionStorePolicyDestination(url: Legal.appleEULA, for: .termsOfService)
        .subscriptionStorePolicyDestination(url: Legal.privacy, for: .privacyPolicy)
        .inAppPurchaseOptions { _ in
            if let token = store.accountToken { return [.appAccountToken(token)] }
            return []
        }
        .onInAppPurchaseCompletion { _, result in
            if case .success(let r) = result { await store.purchased(r) }
            if model.isPro { dismiss() }
        }
        .tint(Palette.gold2)
        .overlay(alignment: .bottom) {
            if let e = store.lastError {
                NoticeText(text: e).padding()
            }
        }
    }
}

/// The top of the paywall: what Pro adds, from the admin-editable Free vs Pro values.
struct ProMarketing: View {
    let plan: MyPlan?
    var reason: String?

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "crown.fill").font(.system(size: 44)).foregroundStyle(Palette.gold)
            Text("StepQ Pro").font(Typo.display(34)).foregroundStyle(.white)
            if let reason {
                Text(reason).font(Typo.body).foregroundStyle(Palette.onNightMuted).multilineTextAlignment(.center)
            }
            VStack(alignment: .leading, spacing: 8) {
                ForEach(perks, id: \.self) { p in
                    Label(p, systemImage: "checkmark.circle.fill").font(Typo.body).foregroundStyle(.white)
                        .labelStyle(PerkLabelStyle())
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(Palette.night)
    }

    /// Pro features that differ from Free, worded from the plan.
    private var perks: [String] {
        let features = plan?.features ?? MyPlan.Companion.shared.FREE_DEFAULTS.features
        return features.compactMap { f in
            let free = f.free?.intValue
            let pro = f.pro?.intValue
            guard free != pro else { return nil }
            if f.isSwitch {
                // Switches that Pro turns off (e.g. "posts wait for approval") read as a perk.
                if f.key == "post_approval" { return "Your posts go live without waiting" }
                return (pro ?? 0) > 0 ? f.label : nil
            }
            let unit = f.unit.map { " \($0)" } ?? ""
            if let pro { return "\(f.label): \(pro)\(unit)" }
            return "\(f.label): unlimited"
        }
    }
}

private struct PerkLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(alignment: .top, spacing: 10) {
            configuration.icon.foregroundStyle(Palette.gold2)
            configuration.title
        }
    }
}

/// Profile → StepQ Pro: membership, or the way to upgrade.
struct ProHubView: View {
    @Environment(AppModel.self) private var model
    @Environment(ProStore.self) private var store
    @State private var manage = false
    @State private var upgrade = false
    @State private var restoring = false

    var body: some View {
        List {
            Section {
                HStack(spacing: 12) {
                    Image(systemName: "crown.fill").font(.title).foregroundStyle(Palette.gold)
                    VStack(alignment: .leading) {
                        Text(model.isPro ? "You're a StepQ Pro member" : "You're on StepQ Free").font(Typo.heading())
                        if model.isPro, let since = model.plan?.proSince, let d = parseTimestamp(since) {
                            Text("Since \(d.formatted(date: .abbreviated, time: .omitted))").font(Typo.small).foregroundStyle(Palette.muted)
                        }
                    }
                }
            }
            if let plan = model.plan {
                Section("Free and Pro") {
                    ForEach(plan.features, id: \.key) { f in
                        HStack {
                            Text(f.label).font(Typo.body)
                            Spacer()
                            Text(value(f, f.free)).font(Typo.small).foregroundStyle(Palette.muted).frame(width: 64)
                            Text(value(f, f.pro)).font(Typo.smallStrong).foregroundStyle(Palette.goldText).frame(width: 64)
                        }
                    }
                }
            }
            Section {
                if model.isPro {
                    Button("Manage subscription") { manage = true }
                } else {
                    Button("Upgrade to StepQ Pro") { upgrade = true }.font(Typo.bodyStrong)
                }
                Button(restoring ? "Restoring…" : "Restore purchases") {
                    Task {
                        restoring = true
                        await store.restore()
                        restoring = false
                    }
                }
                .disabled(restoring)
            } footer: {
                if let e = store.lastError { Text(e) }
            }
        }
        .navigationTitle("StepQ Pro")
        .manageSubscriptionsSheet(isPresented: $manage)
        .sheet(isPresented: $upgrade) { PaywallView() }
        .task { await model.refreshPlan() }
    }

    private func value(_ f: PlanFeature, _ v: KotlinInt?) -> String {
        if f.isSwitch { return (v?.intValue ?? 0) > 0 ? "Yes" : "No" }
        guard let v else { return "Unlimited" }
        return "\(v.intValue)"
    }
}
