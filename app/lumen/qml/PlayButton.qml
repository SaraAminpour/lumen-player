import QtQuick
import QtQuick.Effects
import "."
import "Icons.js" as Icons

// Big gradient play/pause orb with a morphing icon and glow.
Item {
    id: root
    property bool playing: !player.paused
    property real size: 50
    width: size; height: size
    signal clicked()

    Rectangle {
        id: orb
        anchors.fill: parent
        radius: width / 2
        scale: ma.pressed ? 0.88 : ma.containsMouse ? 1.07 : 1
        Behavior on scale { NumberAnimation { duration: 280; easing.type: Easing.OutBack; easing.overshoot: 2.4 } }
        gradient: Gradient {
            orientation: Gradient.Horizontal
            GradientStop { position: 0; color: Theme.accentA }
            GradientStop { position: 1; color: Theme.accentB }
        }
        layer.enabled: true
        layer.effect: MultiEffect {
            shadowEnabled: true
            shadowColor: Theme.accentA
            shadowBlur: 1.0
            shadowOpacity: ma.containsMouse ? 0.85 : 0.5
            shadowVerticalOffset: 2
        }
        Rectangle {
            anchors.fill: parent; radius: parent.radius
            color: "white"; opacity: ma.containsMouse ? 0.12 : 0
            Behavior on opacity { NumberAnimation { duration: Theme.fast } }
        }
        Icon {
            anchors.centerIn: parent
            path: Icons.pause
            size: 28
            color: Theme.onAccent
            opacity: root.playing ? 1 : 0
            scale: root.playing ? 1 : 0.4
            rotation: root.playing ? 0 : -90
            Behavior on opacity { NumberAnimation { duration: 180 } }
            Behavior on scale { NumberAnimation { duration: 320; easing.type: Easing.OutBack } }
            Behavior on rotation { NumberAnimation { duration: 320; easing.type: Easing.OutCubic } }
        }
        Icon {
            anchors.centerIn: parent
            anchors.horizontalCenterOffset: 2
            path: Icons.play
            size: 30
            color: Theme.onAccent
            opacity: root.playing ? 0 : 1
            scale: root.playing ? 0.4 : 1
            rotation: root.playing ? 90 : 0
            Behavior on opacity { NumberAnimation { duration: 180 } }
            Behavior on scale { NumberAnimation { duration: 320; easing.type: Easing.OutBack } }
            Behavior on rotation { NumberAnimation { duration: 320; easing.type: Easing.OutCubic } }
        }
    }
    MouseArea {
        id: ma
        anchors.fill: parent
        hoverEnabled: true
        cursorShape: Qt.PointingHandCursor
        onClicked: root.clicked()
    }
    Tip { visible: ma.containsMouse; text: root.playing ? "Pause (Space)" : "Play (Space)" }
}
