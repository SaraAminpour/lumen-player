import QtQuick
import QtQuick.Controls.Basic
import "."
import "Icons.js" as Icons

MenuItem {
    id: item
    implicitHeight: 34
    implicitWidth: 250
    leftPadding: 12; rightPadding: 12
    contentItem: Item {
        Text {
            anchors.left: parent.left
            anchors.leftMargin: 24
            anchors.right: parent.right
            anchors.rightMargin: item.subMenu ? 18 : 0
            anchors.verticalCenter: parent.verticalCenter
            text: item.text.replace("&", "")
            elide: Text.ElideRight
            color: item.enabled ? Theme.text : Theme.textFaint
            font.family: Theme.font; font.pixelSize: 13
        }
        Icon {
            visible: item.checked
            path: Icons.check
            size: 16
            color: Theme.accent
            anchors.verticalCenter: parent.verticalCenter
        }
        Icon {
            visible: item.subMenu !== null
            anchors.right: parent.right
            anchors.verticalCenter: parent.verticalCenter
            path: Icons.chevronRight
            size: 16
            color: Theme.textDim
        }
    }
    indicator: Item {}
    arrow: Item {}
    background: Rectangle {
        x: 5; width: item.width - 10
        height: item.height
        radius: 9
        color: item.highlighted ? Qt.rgba(Theme.accentA.r, Theme.accentA.g, Theme.accentA.b, 0.32) : "transparent"
        Behavior on color { ColorAnimation { duration: 90 } }
    }
}
