import QtQuick
import "."

// Small selectable chip for option groups (aspect ratios, speeds, ...).
Rectangle {
    id: root
    property string text: ""
    property bool checked: false
    signal clicked()
    implicitWidth: Math.max(44, label.implicitWidth + 22)
    implicitHeight: 30
    radius: 10
    color: checked ? "transparent" : ma.containsMouse ? Theme.cardHover : Theme.card
    border.color: checked ? "transparent" : Theme.glassBorder
    Behavior on color { ColorAnimation { duration: Theme.fast } }
    scale: ma.pressed ? 0.94 : 1
    Behavior on scale { NumberAnimation { duration: 180; easing.type: Easing.OutBack } }

    Rectangle {
        anchors.fill: parent
        radius: parent.radius
        opacity: root.checked ? 1 : 0
        Behavior on opacity { NumberAnimation { duration: Theme.med } }
        gradient: Gradient {
            orientation: Gradient.Horizontal
            GradientStop { position: 0; color: Theme.accentA }
            GradientStop { position: 1; color: Theme.accentB }
        }
    }
    Text {
        id: label
        anchors.centerIn: parent
        text: root.text
        font.family: Theme.font
        font.pixelSize: 12
        font.weight: Font.DemiBold
        font.features: { "tnum": 1 }
        color: root.checked ? Theme.onAccent : Theme.text
    }
    MouseArea {
        id: ma
        anchors.fill: parent
        hoverEnabled: true
        cursorShape: Qt.PointingHandCursor
        onClicked: root.clicked()
    }
}
