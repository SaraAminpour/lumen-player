import QtQuick
import "."

// Round hover-glow icon button with press bounce, optional checked (accent) state and tooltip.
Item {
    id: root
    property alias icon: ic.path
    property string tip: ""
    property real size: 40
    property real iconSize: 22
    property bool checked: false
    property bool danger: false
    property color iconColor: checked ? Theme.accent : Theme.text
    property bool enabledLook: enabled
    readonly property bool hovered: ma.containsMouse
    signal clicked()
    signal rightClicked()

    width: size; height: size
    opacity: enabled ? 1 : 0.35

    Rectangle {
        id: bg
        anchors.fill: parent
        radius: height / 2
        color: root.danger && ma.containsMouse ? "#e11d48"
             : ma.pressed ? Theme.press
             : ma.containsMouse ? Theme.hover
             : root.checked ? Qt.rgba(Theme.accentA.r, Theme.accentA.g, Theme.accentA.b, 0.18) : "transparent"
        Behavior on color { ColorAnimation { duration: Theme.fast } }
    }
    Icon {
        id: ic
        anchors.centerIn: parent
        size: root.iconSize
        color: root.danger && ma.containsMouse ? "white" : root.iconColor
        scale: ma.pressed ? 0.82 : ma.containsMouse ? 1.08 : 1
        Behavior on scale { NumberAnimation { duration: 220; easing.type: Easing.OutBack; easing.overshoot: 3 } }
        Behavior on color { ColorAnimation { duration: Theme.fast } }
    }
    MouseArea {
        id: ma
        anchors.fill: parent
        hoverEnabled: true
        acceptedButtons: Qt.LeftButton | Qt.RightButton
        cursorShape: Qt.PointingHandCursor
        onClicked: (m) => m.button === Qt.RightButton ? root.rightClicked() : root.clicked()
    }
    Tip { visible: root.tip !== "" && ma.containsMouse && !ma.pressed; text: root.tip }
}
