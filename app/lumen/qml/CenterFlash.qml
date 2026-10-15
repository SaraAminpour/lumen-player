import QtQuick
import "."
import "Icons.js" as Icons

// Big icon that blooms and fades in the middle of the video (play/pause feedback).
Item {
    id: root
    width: 96; height: 96
    property string icon: Icons.play
    function flash(ic) { icon = ic; anim.restart() }
    opacity: 0

    Rectangle {
        id: disc
        anchors.fill: parent
        radius: width / 2
        color: Qt.rgba(0, 0, 0, 0.45)
        border.color: Qt.rgba(1, 1, 1, 0.12)
        Icon { anchors.centerIn: parent; path: root.icon; size: 46; color: "white" }
    }
    Rectangle {
        id: ring
        anchors.centerIn: parent
        width: parent.width; height: width; radius: width / 2
        color: "transparent"
        border.width: 2
        border.color: Theme.accentB
    }
    ParallelAnimation {
        id: anim
        NumberAnimation { target: root; property: "opacity"; from: 1; to: 0; duration: 650; easing.type: Easing.InQuad }
        NumberAnimation { target: disc; property: "scale"; from: 0.7; to: 1.15; duration: 650; easing.type: Easing.OutCubic }
        NumberAnimation { target: ring; property: "scale"; from: 0.8; to: 1.8; duration: 650; easing.type: Easing.OutCubic }
        NumberAnimation { target: ring; property: "opacity"; from: 0.9; to: 0; duration: 650 }
    }
}
