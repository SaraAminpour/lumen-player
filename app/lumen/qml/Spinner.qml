import QtQuick
import QtQuick.Shapes
import "."

// Gradient arc spinner.
Item {
    id: root
    width: 56; height: 56
    property bool running: visible
    Shape {
        anchors.fill: parent
        preferredRendererType: Shape.CurveRenderer
        RotationAnimator on rotation { from: 0; to: 360; duration: 900; loops: Animation.Infinite; running: root.running }
        ShapePath {
            strokeWidth: 5
            capStyle: ShapePath.RoundCap
            fillColor: "transparent"
            strokeColor: Theme.accentB
            PathAngleArc { centerX: 28; centerY: 28; radiusX: 23; radiusY: 23; startAngle: 0; sweepAngle: 250 }
        }
    }
}
