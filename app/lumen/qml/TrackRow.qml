import QtQuick
import "."

Rectangle {
    id: row
    property string title: ""
    property string sub: ""
    property bool on: false
    signal hit()
    height: sub === "" ? 40 : 50
    radius: 12
    color: on ? Qt.rgba(Theme.accentA.r, Theme.accentA.g, Theme.accentA.b, 0.16) : rma.containsMouse ? Theme.cardHover : "transparent"
    border.color: on ? Qt.rgba(Theme.accentA.r, Theme.accentA.g, Theme.accentA.b, 0.5) : "transparent"
    Behavior on color { ColorAnimation { duration: Theme.fast } }
    Rectangle {
        id: dot
        x: 12; anchors.verticalCenter: parent.verticalCenter
        width: 18; height: 18; radius: 9
        color: "transparent"
        border.width: 2
        border.color: row.on ? Theme.accent : Theme.textFaint
        Rectangle {
            anchors.centerIn: parent
            width: 8; height: 8; radius: 4
            color: Theme.accent
            scale: row.on ? 1 : 0
            Behavior on scale { NumberAnimation { duration: 260; easing.type: Easing.OutBack; easing.overshoot: 3 } }
        }
    }
    Column {
        anchors.left: dot.right; anchors.leftMargin: 12
        anchors.right: parent.right; anchors.rightMargin: 12
        anchors.verticalCenter: parent.verticalCenter
        spacing: 2
        Text { text: row.title; width: parent.width; elide: Text.ElideRight; color: Theme.text; font.family: Theme.font; font.pixelSize: 13; font.weight: row.on ? Font.DemiBold : Font.Normal }
        Text { visible: row.sub !== ""; text: row.sub; width: parent.width; elide: Text.ElideRight; color: Theme.textFaint; font.family: Theme.font; font.pixelSize: 11 }
    }
    MouseArea { id: rma; anchors.fill: parent; hoverEnabled: true; cursorShape: Qt.PointingHandCursor; onClicked: row.hit() }
}
