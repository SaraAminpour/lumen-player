import QtQuick
import "."
import "Icons.js" as Icons

// Animated OSD pill: show(text, icon, progress[0..1 or -1]).
Item {
    id: root
    property string text: ""
    property string icon: ""
    property real progress: -1
    property bool shown: false
    width: pill.width
    height: 46

    function show(t, ic, p) {
        text = t; icon = ic || ""; progress = (p === undefined ? -1 : p)
        if (!shown) { shown = true; pop.restart() } else bump.restart()
        hideTimer.restart()
    }
    Timer { id: hideTimer; interval: 1400; onTriggered: root.shown = false }

    Rectangle {
        id: pill
        anchors.horizontalCenter: parent.horizontalCenter
        height: 46
        width: row.implicitWidth + 36
        Behavior on width { NumberAnimation { duration: 200; easing.type: Easing.OutCubic } }
        radius: 23
        color: Qt.rgba(0.06, 0.065, 0.09, 0.86)
        border.color: Theme.glassBorder
        opacity: root.shown ? 1 : 0
        scale: root.shown ? 1 : 0.85
        y: root.shown ? 0 : -10
        Behavior on opacity { NumberAnimation { duration: 220 } }
        Behavior on scale { NumberAnimation { duration: 320; easing.type: Easing.OutBack } }
        Behavior on y { NumberAnimation { duration: 320; easing.type: Easing.OutCubic } }

        SequentialAnimation { id: pop }
        SequentialAnimation {
            id: bump
            NumberAnimation { target: pill; property: "scale"; to: 1.05; duration: 80 }
            NumberAnimation { target: pill; property: "scale"; to: 1.0; duration: 220; easing.type: Easing.OutBack }
        }
        Row {
            id: row
            anchors.centerIn: parent
            spacing: 10
            Icon { visible: root.icon !== ""; path: root.icon; size: 20; color: Theme.accent; anchors.verticalCenter: parent.verticalCenter }
            Text {
                text: root.text
                color: Theme.text
                font.family: Theme.font; font.pixelSize: 14; font.weight: Font.DemiBold
                font.features: { "tnum": 1 }
                anchors.verticalCenter: parent.verticalCenter
            }
            Rectangle {
                visible: root.progress >= 0
                width: 90; height: 5; radius: 2.5
                anchors.verticalCenter: parent.verticalCenter
                color: Qt.rgba(1, 1, 1, 0.16)
                Rectangle {
                    width: Math.max(5, parent.width * Math.min(1, root.progress))
                    Behavior on width { NumberAnimation { duration: 120 } }
                    height: parent.height; radius: 2.5
                    gradient: Gradient {
                        orientation: Gradient.Horizontal
                        GradientStop { position: 0; color: Theme.accentA }
                        GradientStop { position: 1; color: Theme.accentB }
                    }
                }
            }
        }
    }
}
