import SwiftUI
import Shared

/// Palettes for the avatar builder, by index as stored in app.profiles.avatar (same as Android).
enum AvatarPalette {
    static let skin: [Color] = [Color(hex: 0xF6D3BC), Color(hex: 0xE2AC82), Color(hex: 0xC98642), Color(hex: 0x8E5426), Color(hex: 0x5A3418)]
    static let hair: [Color] = [Color(hex: 0x2B1A10), Color(hex: 0x5B3A1E), Color(hex: 0xA0522D), Color(hex: 0x8C8C8C), Color(hex: 0xE7C26A)]
    static let outfit: [Color] = [Palette.deep, Palette.leaf, Palette.tomato, Color(hex: 0x3B6FD8), Palette.ink]
    static let headband: [Color] = [Palette.mango, Palette.tomato, Palette.leaf, Color(hex: 0x3B6FD8), .white]
    static let styles = ["Short", "Long", "Bun"]

    static func pick(_ list: [Color], _ i: Int32, _ fallback: Int) -> Color {
        let i = Int(i)
        return list.indices.contains(i) ? list[i] : list[fallback]
    }
}

/// The round avatar: head with hair and headband over a shirt, on a mint disc. Same drawing as
/// Android's StepqAvatar.
struct AvatarView: View {
    let config: AvatarConfig
    var size: CGFloat = 44

    var body: some View {
        Canvas { ctx, s in
            let w = s.width
            let skin = AvatarPalette.pick(AvatarPalette.skin, config.skin, 2)
            let hair = AvatarPalette.pick(AvatarPalette.hair, config.hair, 0)
            let outfit = AvatarPalette.pick(AvatarPalette.outfit, config.outfit, 0)
            let band = AvatarPalette.pick(AvatarPalette.headband, config.headband, 0)
            let disc = Path(ellipseIn: CGRect(x: 0, y: 0, width: w, height: w))
            ctx.fill(disc, with: .color(Palette.mint))
            ctx.drawLayer { layer in
                layer.clip(to: disc)
                let r = w * 0.36
                layer.fill(Path(ellipseIn: CGRect(x: w / 2 - r, y: w * 1.02 - r, width: 2 * r, height: 2 * r)), with: .color(outfit))
            }
            switch config.style {
            case 1:
                ctx.fill(Path(roundedRect: CGRect(x: w * 0.23, y: w * 0.24, width: w * 0.54, height: w * 0.52),
                              cornerSize: CGSize(width: w * 0.22, height: w * 0.18)), with: .color(hair))
            case 2:
                let r = w * 0.09
                ctx.fill(Path(ellipseIn: CGRect(x: w / 2 - r, y: w * 0.19 - r, width: 2 * r, height: 2 * r)), with: .color(hair))
            default:
                break
            }
            ctx.fill(Path(CGRect(x: w * 0.44, y: w * 0.52, width: w * 0.12, height: w * 0.16)), with: .color(skin))
            let head = w * 0.2
            ctx.fill(Path(ellipseIn: CGRect(x: w / 2 - head, y: w * 0.42 - head, width: 2 * head, height: 2 * head)), with: .color(skin))
            // Hair: the top half of an ellipse over the head.
            var top = ellipseArc(center: CGPoint(x: w / 2, y: w * 0.37), rx: w * 0.2, ry: w * 0.15, from: 180, to: 360)
            top.closeSubpath()
            ctx.fill(top, with: .color(hair))
            ctx.fill(Path(roundedRect: CGRect(x: w * 0.28, y: w * 0.32, width: w * 0.44, height: w * 0.06), cornerRadius: w * 0.03), with: .color(band))
            let eye = w * 0.018
            ctx.fill(Path(ellipseIn: CGRect(x: w * 0.43 - eye, y: w * 0.45 - eye, width: 2 * eye, height: 2 * eye)), with: .color(Palette.ink))
            ctx.fill(Path(ellipseIn: CGRect(x: w * 0.57 - eye, y: w * 0.45 - eye, width: 2 * eye, height: 2 * eye)), with: .color(Palette.ink))
            let smile = ellipseArc(center: CGPoint(x: w / 2, y: w * 0.49), rx: w * 0.07, ry: w * 0.05, from: 20, to: 160)
            ctx.stroke(smile, with: .color(Palette.ink), style: StrokeStyle(lineWidth: w * 0.018, lineCap: .round))
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/// Builds an AvatarConfig from Swift.
func makeAvatar(skin: Int, hair: Int, outfit: Int, headband: Int, style: Int) -> AvatarConfig {
    AvatarConfig(skin: Int32(skin), hair: Int32(hair), outfit: Int32(outfit), headband: Int32(headband), style: Int32(style))
}

/// Part of an ellipse, angles in degrees clockwise from 3 o'clock (y grows downwards), like
/// Compose's drawArc.
private func ellipseArc(center: CGPoint, rx: CGFloat, ry: CGFloat, from: Double, to: Double) -> Path {
    var path = Path()
    let steps = 24
    for i in 0...steps {
        let a = (from + (to - from) * Double(i) / Double(steps)) * .pi / 180
        let p = CGPoint(x: center.x + rx * CGFloat(cos(a)), y: center.y + ry * CGFloat(sin(a)))
        if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
    }
    return path
}
