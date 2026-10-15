import QtQuick
import QtQuick.Shapes

// Soft radial glow used for the animated aurora background.
Shape {
    id: s
    property color color: "#8b5cf6"
    property real size: 600
    width: size; height: size
    preferredRendererType: Shape.GeometryRenderer
    ShapePath {
        strokeWidth: -1
        fillGradient: RadialGradient {
            centerX: s.size / 2; centerY: s.size / 2; centerRadius: s.size / 2
            focalX: s.size / 2; focalY: s.size / 2
            GradientStop { position: 0.0; color: Qt.rgba(s.color.r, s.color.g, s.color.b, 0.62) }
            GradientStop { position: 0.42; color: Qt.rgba(s.color.r, s.color.g, s.color.b, 0.24) }
            GradientStop { position: 1.0; color: Qt.rgba(s.color.r, s.color.g, s.color.b, 0.0) }
        }
        PathRectangle { x: 0; y: 0; width: s.size; height: s.size }
    }
}
