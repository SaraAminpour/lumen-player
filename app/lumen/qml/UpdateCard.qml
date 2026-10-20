import QtQuick
import "."
import "Icons.js" as Icons

// "Lumen x.y is available" card: what's new, Install (downloads + installs quietly) or Later.
Rectangle {
    id: card
    property bool dismissed: false
    property bool allowed: true          // only alongside the controls, never over a film being watched
    readonly property string st: updater.state
    readonly property bool shown: !dismissed && (allowed || st !== "available") && (st === "available" || st === "downloading"
                                                  || (st === "error" && wasInstalling))
    property bool wasInstalling: false
    Connections {
        target: updater
        function onChanged() {
            if (updater.state === "downloading") card.wasInstalling = true
            else if (updater.state === "available" || updater.state === "checking") card.wasInstalling = false
        }
    }

    width: 360
    height: col.implicitHeight + 36
    radius: 20
    color: Qt.rgba(0.06, 0.065, 0.09, 0.92)
    border.color: Theme.glassBorder
    opacity: shown ? 1 : 0
    visible: opacity > 0
    scale: shown ? 1 : 0.92
    transform: Translate { y: card.shown ? 0 : -12 }
    Behavior on opacity { NumberAnimation { duration: 260 } }
    Behavior on scale { NumberAnimation { duration: 380; easing.type: Easing.OutBack } }
    MouseArea { anchors.fill: parent; acceptedButtons: Qt.AllButtons }   // keep clicks off the video

    Column {
        id: col
        x: 18; y: 18
        width: parent.width - 36
        spacing: 12
        Row {
            spacing: 12
            Rectangle {
                width: 40; height: 40; radius: 20
                gradient: Gradient {
                    orientation: Gradient.Horizontal
                    GradientStop { position: 0; color: Theme.accentA }
                    GradientStop { position: 1; color: Theme.accentB }
                }
                Icon { anchors.centerIn: parent; path: Icons.cloudDownloadOutline; size: 22; color: "white" }
                SequentialAnimation on scale {
                    running: card.shown && card.st === "available"; loops: Animation.Infinite
                    NumberAnimation { to: 1.08; duration: 900; easing.type: Easing.InOutSine }
                    NumberAnimation { to: 1.0; duration: 900; easing.type: Easing.InOutSine }
                }
            }
            Column {
                anchors.verticalCenter: parent.verticalCenter
                spacing: 2
                Text {
                    text: card.st === "error" ? "Update didn't finish" : "Lumen " + updater.latest + " is ready"
                    color: Theme.text; font.family: Theme.font; font.pixelSize: 15; font.weight: Font.Bold
                }
                Text {
                    text: card.st === "available" ? "You have " + updater.current + "  ·  installs in about a minute" : updater.message
                    color: card.st === "error" ? Theme.danger : Theme.textDim
                    font.family: Theme.font; font.pixelSize: 12
                    width: col.width - 52; elide: Text.ElideRight
                }
            }
        }
        Text {
            visible: card.st === "available" && updater.notes !== ""
            width: parent.width
            text: updater.notes
            wrapMode: Text.WordWrap
            maximumLineCount: 6
            elide: Text.ElideRight
            lineHeight: 1.15
            color: Theme.textDim; font.family: Theme.font; font.pixelSize: 12
        }
        Rectangle {   // download progress
            visible: card.st === "downloading"
            width: parent.width; height: 6; radius: 3
            color: Qt.rgba(1, 1, 1, 0.12)
            Rectangle {
                width: Math.max(parent.height, parent.width * updater.progress); height: parent.height; radius: 3
                Behavior on width { SmoothedAnimation { velocity: 400 } }
                gradient: Gradient {
                    orientation: Gradient.Horizontal
                    GradientStop { position: 0; color: Theme.accentA }
                    GradientStop { position: 1; color: Theme.accentB }
                }
            }
        }
        Row {
            anchors.right: parent.right
            spacing: 8
            visible: card.st !== "downloading"
            PillButton { text: "Later"; h: 34; fontSize: 12; onClicked: card.dismissed = true }
            PillButton {
                primary: true; h: 34; fontSize: 12; hpad: 18
                icon: card.st === "error" ? Icons.restore : Icons.cloudDownloadOutline
                text: card.st === "error" ? "Try again" : "Install now"
                onClicked: updater.install()
            }
        }
    }
}
