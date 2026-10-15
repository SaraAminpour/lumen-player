import QtQuick
import "."

// Dimmed backdrop + centered card that springs in. Set open = true/false.
Item {
    id: root
    property bool open: false
    property real cardWidth: 520
    property real cardHeight: 300
    default property alias content: card.data
    anchors.fill: parent
    visible: opacity > 0.01
    opacity: open ? 1 : 0
    Behavior on opacity { NumberAnimation { duration: 220 } }
    z: 50

    Rectangle {
        anchors.fill: parent
        color: Qt.rgba(0, 0, 0, 0.55)
        MouseArea { anchors.fill: parent; acceptedButtons: Qt.AllButtons; onClicked: root.open = false; onWheel: (w) => w.accepted = true }
    }
    Rectangle {
        id: card
        anchors.centerIn: parent
        width: Math.min(root.cardWidth, root.width - 40)
        height: Math.min(root.cardHeight, root.height - 40)
        radius: 24
        color: "#12141b"
        border.color: Theme.glassBorder
        scale: root.open ? 1 : 0.92
        Behavior on scale { NumberAnimation { duration: 380; easing.type: Easing.OutBack; easing.overshoot: 1.3 } }
        MouseArea { anchors.fill: parent; acceptedButtons: Qt.AllButtons }
    }
}
