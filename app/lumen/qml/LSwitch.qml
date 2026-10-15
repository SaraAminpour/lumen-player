import QtQuick
import "."

// Row with a label, optional hint and an animated toggle switch.
Item {
    id: root
    property string text: ""
    property string hint: ""
    property bool checked: false
    signal toggled(bool on)
    implicitHeight: Math.max(36, col.implicitHeight + 10)
    implicitWidth: 300

    Column {
        id: col
        anchors.left: parent.left
        anchors.right: sw.left
        anchors.rightMargin: 12
        anchors.verticalCenter: parent.verticalCenter
        spacing: 2
        Text { text: root.text; color: Theme.text; font.family: Theme.font; font.pixelSize: 13; width: parent.width; elide: Text.ElideRight }
        Text { visible: root.hint !== ""; text: root.hint; color: Theme.textFaint; font.family: Theme.font; font.pixelSize: 11; width: parent.width; wrapMode: Text.WordWrap }
    }
    Rectangle {
        id: sw
        anchors.right: parent.right
        anchors.verticalCenter: parent.verticalCenter
        width: 40; height: 22; radius: 11
        color: root.checked ? "transparent" : Qt.rgba(1, 1, 1, 0.16)
        Rectangle {
            anchors.fill: parent; radius: parent.radius
            opacity: root.checked ? 1 : 0
            Behavior on opacity { NumberAnimation { duration: Theme.med } }
            gradient: Gradient {
                orientation: Gradient.Horizontal
                GradientStop { position: 0; color: Theme.accentA }
                GradientStop { position: 1; color: Theme.accentB }
            }
        }
        Rectangle {
            width: ma.pressed ? 22 : 16; height: 16; radius: 8
            Behavior on width { NumberAnimation { duration: 160 } }
            y: 3
            x: root.checked ? parent.width - width - 3 : 3
            Behavior on x { NumberAnimation { duration: 300; easing.type: Easing.OutBack; easing.overshoot: 1.6 } }
            color: "white"
        }
    }
    MouseArea {
        id: ma
        anchors.fill: parent
        hoverEnabled: true
        cursorShape: Qt.PointingHandCursor
        onClicked: root.toggled(!root.checked)
    }
}
