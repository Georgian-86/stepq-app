import SwiftUI
import Shared

/// Palettes for the avatar builder, by index as stored in app.profiles.avatar. They come from the
/// shared AvatarArt, so Android and iOS always offer the same colours.
enum AvatarPalette {
    static let skin = AvatarArt.shared.skin.map { Color(argb: $0.int64Value) }
    static let hair = AvatarArt.shared.hair.map { Color(argb: $0.int64Value) }
    static let outfit = AvatarArt.shared.outfit.map { Color(argb: $0.int64Value) }
    static let headband = AvatarArt.shared.headband.map { Color(argb: $0.int64Value) }
}

/// The avatar choices as plain Swift values, so the builder can edit them one key path at a time.
struct AvatarLook: Equatable {
    var skin = 2
    var hair = 0
    var outfit = 0
    var headband = 0
    var style = 0
    var body = -1
    var top = 0
    var headwear = 0
    var eyewear = 0
    var facialHair = 0
    var extra = 0

    init() {}

    init(_ a: AvatarConfig) {
        skin = Int(a.skin)
        hair = Int(a.hair)
        outfit = Int(a.outfit)
        headband = Int(a.headband)
        style = Int(a.style)
        body = Int(a.body)
        top = Int(a.top)
        headwear = Int(a.headwear)
        eyewear = Int(a.eyewear)
        facialHair = Int(a.facialHair)
        extra = Int(a.extra)
    }

    var config: AvatarConfig {
        makeAvatar(skin: skin, hair: hair, outfit: outfit, headband: headband, style: style, body: body,
                   top: top, headwear: headwear, eyewear: eyewear, facialHair: facialHair, extra: extra)
    }

    func with(_ key: WritableKeyPath<AvatarLook, Int>, _ value: Int) -> AvatarLook {
        var copy = self
        copy[keyPath: key] = value
        return copy
    }
}

/// The round avatar, drawn from the shared AvatarArt shapes, the same ones Android's
/// StepqAvatar draws.
struct AvatarView: View {
    let config: AvatarConfig
    var size: CGFloat = 44

    var body: some View {
        Canvas { ctx, s in
            let w = s.width
            let disc = Path(ellipseIn: CGRect(x: 0, y: 0, width: w, height: w))
            for shape in AvatarArt.shared.shapes(config: config) {
                guard let (path, stroke) = Self.path(shape, w) else { continue }
                let color = Color(argb: shape.argb)
                if shape.clipToDisc {
                    ctx.drawLayer { layer in
                        layer.clip(to: disc)
                        Self.paint(layer, path, color, stroke)
                    }
                } else {
                    Self.paint(ctx, path, color, stroke)
                }
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    /// The shape's path in points, and its stroke width (0 to fill).
    private static func path(_ shape: AvatarShape, _ w: CGFloat) -> (Path, CGFloat)? {
        func f(_ v: Float) -> CGFloat { CGFloat(v) * w }
        if let o = shape as? AvatarOval {
            return (Path(ellipseIn: CGRect(x: f(o.cx - o.rx), y: f(o.cy - o.ry), width: f(o.rx * 2), height: f(o.ry * 2))), f(o.stroke))
        }
        if let r = shape as? AvatarRoundRect {
            let rect = CGRect(x: f(r.x), y: f(r.y), width: f(r.w), height: f(r.h))
            return (Path(roundedRect: rect, cornerSize: CGSize(width: f(r.rx), height: f(r.ry))), 0)
        }
        if let p = shape as? AvatarPoly {
            var path = Path()
            let n = Int(p.points.size) / 2
            for i in 0..<n {
                let pt = CGPoint(x: f(p.points.get(index: Int32(i * 2))), y: f(p.points.get(index: Int32(i * 2 + 1))))
                if i == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
            }
            if p.stroke <= 0 { path.closeSubpath() }
            return (path, f(p.stroke))
        }
        return nil
    }

    private static func paint(_ ctx: GraphicsContext, _ path: Path, _ color: Color, _ stroke: CGFloat) {
        if stroke > 0 {
            ctx.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: stroke, lineCap: .round, lineJoin: .round))
        } else {
            ctx.fill(path, with: .color(color))
        }
    }
}

extension Color {
    /// A colour from 0xAARRGGBB, as the shared code stores them.
    init(argb: Int64) {
        self.init(
            .sRGB,
            red: Double((argb >> 16) & 0xFF) / 255,
            green: Double((argb >> 8) & 0xFF) / 255,
            blue: Double(argb & 0xFF) / 255,
            opacity: Double((argb >> 24) & 0xFF) / 255
        )
    }
}

/// Builds an AvatarConfig from Swift. Choices left out keep their defaults.
func makeAvatar(skin: Int, hair: Int, outfit: Int, headband: Int, style: Int, body: Int = -1, top: Int = 0,
                headwear: Int = 0, eyewear: Int = 0, facialHair: Int = 0, extra: Int = 0) -> AvatarConfig {
    AvatarConfig(skin: Int32(skin), hair: Int32(hair), outfit: Int32(outfit), headband: Int32(headband), style: Int32(style),
                 body: Int32(body), top: Int32(top), headwear: Int32(headwear), eyewear: Int32(eyewear),
                 facialHair: Int32(facialHair), extra: Int32(extra))
}
