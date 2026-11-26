import Foundation
import Observation
import StoreKit
import Shared

/// StepQ Pro through the App Store, with StoreKit 2.
///
/// - Pro is sold only as an in-app subscription (Guideline 3.1.1); the iOS app has no other way
///   to pay and links to none.
/// - Every purchase, renewal and restore is sent to the `appstore-verify` Edge Function, which
///   checks Apple's signature and turns Pro on for the StepQ account. The app never grants Pro.
/// - Each purchase carries the StepQ user id as its appAccountToken, so a subscription can't be
///   moved to another StepQ account.
@MainActor
@Observable
final class ProStore {
    /// Must match the subscriptions in App Store Connect (one group, "StepQ Pro").
    static let productIDs = ["in.stepq.app.pro.monthly", "in.stepq.app.pro.yearly"]

    private let api: StepqApi
    @ObservationIgnored private var updates: Task<Void, Never>?
    /// Called after the server changed my plan, so screens reload it.
    @ObservationIgnored var onPlanChanged: (() async -> Void)?

    private(set) var lastError: String?

    init(api: StepqApi) {
        self.api = api
    }

    /// The StepQ user id as a UUID, for appAccountToken.
    var accountToken: UUID? { api.myId().flatMap(UUID.init(uuidString:)) }

    /// Starts listening for renewals, refunds, Ask to Buy approvals and purchases made on
    /// another device, and re-checks current subscriptions with the server.
    func start() async {
        if updates == nil {
            updates = Task { [weak self] in
                for await result in Transaction.updates {
                    await self?.handle(result)
                }
            }
        }
        for await result in Transaction.currentEntitlements {
            await handle(result, quiet: true)
        }
    }

    func stop() async {
        updates?.cancel()
        updates = nil
    }

    /// Called by the subscription store after a purchase.
    func purchased(_ result: Product.PurchaseResult) async {
        switch result {
        case .success(let verification):
            await handle(verification)
        case .pending:
            lastError = "Your purchase is waiting for approval. Pro turns on as soon as it's approved."
        case .userCancelled:
            break
        @unknown default:
            break
        }
    }

    /// "Restore purchases": asks the App Store for this Apple ID's purchases and checks them.
    func restore() async {
        lastError = nil
        do {
            try await AppStore.sync()
            var found = false
            for await result in Transaction.currentEntitlements {
                found = true
                await handle(result)
            }
            if !found { lastError = "There's no StepQ Pro subscription on this Apple ID." }
        } catch {
            lastError = "Couldn't reach the App Store. Try again."
        }
    }

    private func handle(_ result: VerificationResult<Transaction>, quiet: Bool = false) async {
        guard case .verified(let transaction) = result else {
            if !quiet { lastError = "The App Store couldn't confirm that purchase." }
            return
        }
        guard Self.productIDs.contains(transaction.productID) else {
            await transaction.finish()
            return
        }
        do {
            _ = try await api.verifyAppStorePurchase(signedTransaction: result.jwsRepresentation)
            await transaction.finish()
            lastError = nil
            await onPlanChanged?()
        } catch {
            // Not finished, so StoreKit offers it again next time and nothing is lost.
            if !quiet { lastError = error.localizedDescription }
        }
    }
}
