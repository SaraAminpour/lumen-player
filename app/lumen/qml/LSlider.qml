import QtQuick
import "."

// Gradient slider with springy knob. Emits moved(value) while dragging.
Item {
    id: root
    property real from: 0
    property real to: 1
    property real value: 0
    property real stepSize: 0
    property real defaultValue: NaN          // double-click resets to this
    property bool centered: false            // fill from the middle (for ±ranges)
    property color warnAbove: "transparent"
    property real warnFrom: NaN
    readonly property bool pressed: ma.pressed
    readonly property bool hovered: ma.containsMouse
    property real visualValue: pressed ? dragValue : value
    property real dragValue: 0
    signal moved(real v)

    implicitWidth: 160
    implicitHeight: 24

    function frac(v) { return to === from ? 0 : Math.max(0, Math.min(1, (v - from) / (to - from))) }
    function valAt(x) {
        var v = from + Math.max(0, Math.min(1, x / track.width)) * (to - from)
        if (stepSize > 0) v = Math.round(v / stepSize) * stepSize
        return Math.max(Math.min(from, to), Math.min(Math.max(from, to), v))
    }

    Rectangle {
        id: track
        anchors.verticalCenter: parent.verticalCenter
        width: parent.width
        height: ma.containsMouse || ma.pressed ? 6 : 4
        Behavior on height { NumberAnimation { duration: Theme.fast } }
        radius: height / 2
        color: Qt.rgba(1, 1, 1, 0.14)

        Rectangle {
            id: fill
            readonly property real f: root.frac(root.visualValue)
            readonly property real c: root.frac(Math.max(root.from, Math.min(root.to, 0)))
            x: root.centered ? Math.min(f, c) * track.width : 0
            width: root.centered ? Math.abs(f - c) * track.width : f * track.width
            height: parent.height
            radius: height / 2
            gradient: Gradient {
                orientation: Gradient.Horizontal
                GradientStop { position: 0; color: Theme.accentA }
                GradientStop { position: 1; color: Theme.accentB }
            }
        }
        Rectangle {   // "boost" zone (e.g. volume > 100%)
            visible: !isNaN(root.warnFrom) && root.visualValue > root.warnFrom
            x: root.frac(root.warnFrom) * track.width
            width: Math.max(0, (root.frac(root.visualValue) - root.frac(root.warnFrom)) * track.width)
            height: parent.height
            radius: height / 2
            color: root.warnAbove
        }
    }
    Rectangle {
        id: knob
        width: 14; height: 14; radius: 7
        color: "white"
        x: root.frac(root.visualValue) * track.width - width / 2
        anchors.verticalCenter: parent.verticalCenter
        scale: ma.pressed ? 1.25 : ma.containsMouse ? 1 : 0.0
        Behavior on scale { NumberAnimation { duration: 240; easing.type: Easing.OutBack; easing.overshoot: 2.5 } }
        Rectangle {
            anchors.centerIn: parent
            width: 26; height: 26; radius: 13
            color: Theme.accentA
            opacity: ma.pressed ? 0.28 : 0
            z: -1
            Behavior on opacity { NumberAnimation { duration: Theme.fast } }
        }
    }
    MouseArea {
        id: ma
        anchors.fill: parent
        anchors.topMargin: -6; anchors.bottomMargin: -6
        hoverEnabled: true
        cursorShape: Qt.PointingHandCursor
        preventStealing: true
        onPressed: (m) => { root.dragValue = root.valAt(m.x); root.moved(root.dragValue) }
        onPositionChanged: (m) => { if (pressed) { root.dragValue = root.valAt(m.x); root.moved(root.dragValue) } }
        onDoubleClicked: if (!isNaN(root.defaultValue)) root.moved(root.defaultValue)
        onWheel: (w) => {
            var step = root.stepSize > 0 ? root.stepSize : (root.to - root.from) / 50
            var v = Math.max(Math.min(root.from, root.to), Math.min(Math.max(root.from, root.to), root.value + (w.angleDelta.y > 0 ? step : -step)))
            root.moved(v)
        }
    }
}
