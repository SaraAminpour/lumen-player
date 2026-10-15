import QtQuick
import QtQuick.Effects
import "."

// Frosted-glass card: blurs the live video behind it (optional) and tints it.
Item {
    id: g
    property real radius: 20
    property Item source: null
    property real sx: 0
    property real sy: 0
    property real tint: 0.72
    readonly property bool blurOn: prefs.glassBlur && source !== null && source.visible && width > 0 && height > 0

    ShaderEffectSource {
        id: ses
        sourceItem: g.blurOn ? g.source : null
        sourceRect: Qt.rect(g.sx, g.sy, g.width, g.height)
        width: g.width; height: g.height
        visible: false
        live: true
        hideSource: false
    }
    MultiEffect {
        anchors.fill: parent
        visible: g.blurOn
        source: ses
        autoPaddingEnabled: false
        blurEnabled: true
        blur: 1.0
        blurMax: 56
        blurMultiplier: 0.6
        saturation: 0.25
        maskEnabled: true
        maskSource: mask
    }
    Item {
        id: mask
        anchors.fill: parent
        layer.enabled: true
        visible: false
        Rectangle { anchors.fill: parent; radius: g.radius }
    }
    Rectangle {
        anchors.fill: parent
        radius: g.radius
        color: g.blurOn ? Qt.rgba(0.045, 0.05, 0.075, g.tint) : Theme.glass
        border.color: Theme.glassBorder
    }
    Rectangle {
        anchors.fill: parent
        anchors.margins: 1
        radius: g.radius - 1
        gradient: Gradient {
            GradientStop { position: 0; color: Qt.rgba(1, 1, 1, 0.07) }
            GradientStop { position: 0.35; color: Qt.rgba(1, 1, 1, 0.0) }
        }
    }
}
