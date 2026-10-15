import QtQuick
import QtQuick.Shapes
import "."

// Material Design Icon rendered as a vector path (24x24 viewBox), tintable and animatable.
Item {
    id: root
    property string path: ""
    property color color: Theme.text
    property real size: 22
    implicitWidth: size
    implicitHeight: size
    width: size
    height: size

    Shape {
        anchors.centerIn: parent
        width: 24; height: 24
        scale: root.size / 24
        preferredRendererType: Shape.CurveRenderer
        ShapePath {
            strokeWidth: -1
            fillColor: root.color
            PathSvg { path: root.path }
        }
    }
}
