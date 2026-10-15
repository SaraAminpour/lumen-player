import QtQuick
import "."

// Text (+ optional icon) button. primary = accent gradient, otherwise glass.
Item {
    id: root
    property string text: ""
    property string icon: ""
    property bool primary: false
    property bool checked: false
    property real fontSize: 13
    property real hpad: 16
    property real h: 38
    property string tip: ""
    readonly property bool hovered: ma.containsMouse
    signal clicked()

    implicitWidth: row.implicitWidth + hpad * 2
    implicitHeight: h
    opacity: enabled ? 1 : 0.4

    Rectangle {
        id: bg
        anchors.fill: parent
        radius: height / 2
        scale: ma.pressed ? 0.96 : 1
        Behavior on scale { NumberAnimation { duration: 200; easing.type: Easing.OutBack } }
        gradient: root.primary || root.checked ? grad : null
        color: ma.pressed ? Theme.press : ma.containsMouse ? Theme.cardHover : Theme.card
        border.color: root.primary || root.checked ? "transparent" : Theme.glassBorder
        Behavior on color { ColorAnimation { duration: Theme.fast } }
        Gradient {
            id: grad
            orientation: Gradient.Horizontal
            GradientStop { position: 0; color: Theme.accentA }
            GradientStop { position: 1; color: Theme.accentB }
        }
        Rectangle {   // hover sheen
            anchors.fill: parent
            radius: parent.radius
            color: "white"
            opacity: (root.primary || root.checked) && ma.containsMouse ? 0.14 : 0
            Behavior on opacity { NumberAnimation { duration: Theme.fast } }
        }
    }
    Row {
        id: row
        anchors.centerIn: parent
        spacing: 8
        Icon {
            visible: root.icon !== ""
            path: root.icon
            size: root.fontSize + 5
            anchors.verticalCenter: parent.verticalCenter
            color: root.primary || root.checked ? Theme.onAccent : Theme.text
        }
        Text {
            visible: root.text !== ""
            text: root.text
            anchors.verticalCenter: parent.verticalCenter
            font.family: Theme.font
            font.pixelSize: root.fontSize
            font.weight: Font.DemiBold
            color: root.primary || root.checked ? Theme.onAccent : Theme.text
        }
    }
    MouseArea {
        id: ma
        anchors.fill: parent
        hoverEnabled: true
        cursorShape: Qt.PointingHandCursor
        onClicked: root.clicked()
    }
    Tip { visible: root.tip !== "" && ma.containsMouse; text: root.tip }
}
