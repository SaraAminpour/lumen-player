import QtQuick
import "."
import "Icons.js" as Icons

// YouTube-style side ripple with accumulated seek amount.
Item {
    id: root
    property bool forward: true
    property real total: 0
    anchors.top: parent.top
    anchors.bottom: parent.bottom
    width: parent.width * 0.28
    opacity: 0
    clip: true

    function hit(sec) {
        total = fade.running || opacity > 0.05 ? total + sec : sec
        anim.restart()
        hold.restart()
    }
    Timer { id: hold; interval: 650; onTriggered: fade.restart() }

    Rectangle {
        id: blob
        width: root.height * 1.2
        height: width
        radius: width / 2
        x: root.forward ? root.width * 0.25 : root.width * 0.75 - width
        anchors.verticalCenter: parent.verticalCenter
        color: Qt.rgba(1, 1, 1, 0.10)
    }
    Column {
        anchors.centerIn: parent
        spacing: 6
        Row {
            anchors.horizontalCenter: parent.horizontalCenter
            spacing: -6
            Repeater {
                model: 3
                delegate: Icon {
                    required property int index
                    path: root.forward ? Icons.play : Icons.play
                    rotation: root.forward ? 0 : 180
                    size: 26
                    color: "white"
                    opacity: 0.3
                    SequentialAnimation on opacity {
                        running: root.opacity > 0
                        loops: Animation.Infinite
                        PauseAnimation { duration: (root.forward ? index : 2 - index) * 120 }
                        NumberAnimation { to: 1; duration: 160 }
                        NumberAnimation { to: 0.3; duration: 260 }
                        PauseAnimation { duration: (root.forward ? 2 - index : index) * 120 }
                    }
                }
            }
        }
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: (root.total > 0 ? "+" : "−") + Math.abs(root.total) + " s"
            color: "white"
            font.family: Theme.font; font.pixelSize: 15; font.weight: Font.Bold
        }
    }
    ParallelAnimation {
        id: anim
        NumberAnimation { target: root; property: "opacity"; to: 1; duration: 120 }
        NumberAnimation { target: blob; property: "scale"; from: 0.85; to: 1; duration: 380; easing.type: Easing.OutCubic }
    }
    NumberAnimation { id: fade; target: root; property: "opacity"; to: 0; duration: 380 }
}
