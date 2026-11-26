import SwiftUI
import Shared

/// Piko, StepQ's mascot, in one of its poses (vector art from the Android app).
struct Piko: View {
    enum Pose: String { case happy = "piko-happy", cheer = "piko-cheer", nudge = "piko-nudge", body = "piko-body" }
    var pose: Pose = .happy
    var size: CGFloat = 120

    var body: some View {
        Image(pose.rawValue)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

/// An error or notice line under a form.
struct NoticeText: View {
    let text: String
    var isError = true

    var body: some View {
        Text(text)
            .font(Typo.small)
            .foregroundStyle(isError ? Palette.tomatoText : Palette.deep)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background((isError ? Palette.tomatoSoft : Palette.leafSoft), in: RoundedRectangle(cornerRadius: 12))
    }
}

/// A labelled text field in StepQ's style.
struct LabeledField: View {
    let label: String
    @Binding var text: String
    var prompt: String = ""
    var secure = false
    var keyboard: UIKeyboardType = .default
    var content: UITextContentType?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(Typo.smallStrong).foregroundStyle(Palette.muted)
            Group {
                if secure {
                    SecureField(prompt, text: $text)
                } else {
                    TextField(prompt, text: $text)
                        .keyboardType(keyboard)
                }
            }
            .textContentType(content)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .font(Typo.body)
            .padding(.horizontal, 14)
            .frame(minHeight: 50)
            .background(.white, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Palette.line))
        }
    }
}

/// The small gold crown next to StepQ Pro members' names.
struct ProBadge: View {
    var body: some View {
        Image(systemName: "crown.fill")
            .font(.caption2)
            .foregroundStyle(Palette.gold)
            .accessibilityLabel("StepQ Pro")
    }
}

struct LoadingView: View {
    var label = "Loading"

    var body: some View {
        VStack(spacing: 12) {
            ProgressView()
            Text(label).font(Typo.small).foregroundStyle(Palette.muted)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// A full-page error with a retry button.
struct ErrorView: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Piko(pose: .nudge, size: 96)
            Text(message).font(Typo.body).foregroundStyle(Palette.ink).multilineTextAlignment(.center)
            Button("Try again", action: retry).buttonStyle(PrimaryButtonStyle()).frame(maxWidth: 220)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// "By continuing you agree to the Terms of use and Privacy policy", with both as links.
struct LegalLine: View {
    var before = "By continuing you agree to StepQ's "
    var after = "."

    var body: some View {
        Text(.init("\(before)[Terms of use](\(Legal.terms.absoluteString)) and [Privacy policy](\(Legal.privacy.absoluteString))\(after)"))
            .font(Typo.small)
            .foregroundStyle(Palette.muted)
            .tint(Palette.deep)
    }
}

func formatSteps(_ n: Int) -> String {
    n.formatted(.number.grouping(.automatic))
}

func formatSteps(_ n: Int32) -> String { formatSteps(Int(n)) }

/// "yyyy-MM-dd" for a date in the phone's time zone, the way steps are saved.
func isoDay(_ date: Date) -> String {
    let f = DateFormatter()
    f.calendar = Calendar(identifier: .gregorian)
    f.locale = Locale(identifier: "en_US_POSIX")
    f.timeZone = .current
    f.dateFormat = "yyyy-MM-dd"
    return f.string(from: date)
}

func dayFromIso(_ s: String) -> Date? {
    let f = DateFormatter()
    f.calendar = Calendar(identifier: .gregorian)
    f.locale = Locale(identifier: "en_US_POSIX")
    f.timeZone = .current
    f.dateFormat = "yyyy-MM-dd"
    return f.date(from: s)
}

/// Postgres timestamps ("2026-11-26T06:48:18.123+00:00").
func parseTimestamp(_ s: String) -> Date? {
    let f = ISO8601DateFormatter()
    f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    if let d = f.date(from: s) { return d }
    f.formatOptions = [.withInternetDateTime]
    return f.date(from: s)
}
