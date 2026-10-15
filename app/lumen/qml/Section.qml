import QtQuick
import "."

// Card with a heading; children go into the inner column.
Rectangle {
    id: root
    property string title: ""
    property string icon: ""
    property alias trailing: trail.data
    default property alias content: inner.data
    width: parent ? parent.width : 300
    implicitHeight: inner.implicitHeight + head.height + 28
    radius: 16
    color: Theme.card
    border.color: Theme.glassBorder

    Item {
        id: head
        x: 14; y: 12
        width: parent.width - 28
        height: root.title === "" ? 0 : 22
        visible: root.title !== ""
        Row {
            spacing: 8
            anchors.verticalCenter: parent.verticalCenter
            Icon { visible: root.icon !== ""; path: root.icon; size: 16; color: Theme.accent; anchors.verticalCenter: parent.verticalCenter }
            Text {
                text: root.title.toUpperCase()
                color: Theme.textDim
                font.family: Theme.font; font.pixelSize: 11; font.weight: Font.Bold; font.letterSpacing: 1.1
                anchors.verticalCenter: parent.verticalCenter
            }
        }
        Row { id: trail; anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter; spacing: 4 }
    }
    Column {
        id: inner
        x: 14
        y: head.y + head.height + (root.title === "" ? 2 : 10)
        width: parent.width - 28
        spacing: 10
    }
}
