import QtQuick
import QtQuick.Window
import QtQuick.Effects
import "."
import "Icons.js" as Icons

// One-window installer shown by "Install Lumen.exe".
Window {
    id: win
    width: 540; height: 360
    visible: true
    color: Theme.bg
    flags: Qt.Window | Qt.FramelessWindowHint
    title: installer.updating ? "Updating Lumen" : "Install Lumen Player"

    readonly property string st: installer.state
    Component.onCompleted: { Theme.font = uiFont; if (installer.auto) installer.start() }

    onStChanged: if ((st === "done" || st === "error") && installer.auto) shotTimer.start()
    Timer {
        id: shotTimer
        interval: 900
        onTriggered: {
            if (testShot === "") { Qt.quit(); return }
            content.grabToImage(function (r) { r.saveToFile(testShot.split("@")[0]); Qt.quit() })
        }
    }

    Item {
        id: content
        anchors.fill: parent
        clip: true

        Rectangle { anchors.fill: parent; color: Theme.bg }
        Blob {
            color: Theme.accentA; size: 520
            x: -200 + 40 * Math.sin(phase); y: -260 + 30 * Math.cos(phase)
            property real phase: 0
            NumberAnimation on phase { from: 0; to: Math.PI * 2; duration: 14000; loops: Animation.Infinite }
        }
        Blob {
            color: Theme.accentB; size: 460
            x: win.width - 240 + 40 * Math.cos(phase); y: win.height - 220 + 30 * Math.sin(phase)
            property real phase: 0
            NumberAnimation on phase { from: 0; to: Math.PI * 2; duration: 17000; loops: Animation.Infinite }
        }
        Rectangle { anchors.fill: parent; color: "transparent"; border.color: Theme.glassBorder }

        MouseArea {   // drag the window from anywhere
            anchors.fill: parent
            property point p
            onPressed: (m) => p = Qt.point(m.x, m.y)
            onPositionChanged: (m) => { win.x += m.x - p.x; win.y += m.y - p.y }
        }

        IconButton {
            anchors { top: parent.top; right: parent.right; margins: 10 }
            icon: Icons.close
            visible: win.st !== "working"
            onClicked: Qt.quit()
        }

        Column {
            anchors.centerIn: parent
            anchors.verticalCenterOffset: -6
            spacing: 0
            width: 420

            Item {
                width: parent.width; height: 104
                Image {
                    id: logo
                    source: "../assets/lumen.png"
                    width: 84; height: 84
                    sourceSize: Qt.size(168, 168)
                    anchors.horizontalCenter: parent.horizontalCenter
                    y: 6 + 5 * Math.sin(bob)
                    property real bob: 0
                    NumberAnimation on bob { from: 0; to: Math.PI * 2; duration: 3200; loops: Animation.Infinite }
                    scale: win.st === "done" ? 0.0 : 1
                    opacity: win.st === "done" ? 0 : 1
                    Behavior on scale { NumberAnimation { duration: 320; easing.type: Easing.InBack } }
                    Behavior on opacity { NumberAnimation { duration: 260 } }
                    layer.enabled: true
                    layer.effect: MultiEffect { shadowEnabled: true; shadowColor: Theme.accentA; shadowBlur: 1.0; shadowOpacity: 0.7 }
                }
                Rectangle {   // success badge
                    width: 84; height: 84; radius: 42
                    anchors.horizontalCenter: parent.horizontalCenter
                    y: 6
                    gradient: Gradient {
                        orientation: Gradient.Horizontal
                        GradientStop { position: 0; color: Theme.accentA }
                        GradientStop { position: 1; color: Theme.accentB }
                    }
                    scale: win.st === "done" ? 1 : 0
                    Behavior on scale { SequentialAnimation { PauseAnimation { duration: 200 } NumberAnimation { duration: 520; easing.type: Easing.OutBack; easing.overshoot: 2.2 } } }
                    Icon { anchors.centerIn: parent; path: Icons.check; size: 50; color: "white" }
                }
            }

            Text {
                width: parent.width
                horizontalAlignment: Text.AlignHCenter
                text: win.st === "done" ? (installer.updating ? "Lumen is up to date" : "Lumen is installed")
                    : win.st === "error" ? "Something went wrong" : installer.updating ? "Updating Lumen" : "Lumen Player"
                font.family: Theme.font; font.pixelSize: 26; font.weight: Font.Bold
                color: Theme.text
            }
            Item { width: 1; height: 6 }
            Text {
                width: parent.width
                horizontalAlignment: Text.AlignHCenter
                wrapMode: Text.Wrap
                text: win.st === "ready" ? "A modern player for every video. Installs just for you — no admin rights needed."
                    : installer.message
                font.family: Theme.font; font.pixelSize: 13
                color: win.st === "error" ? Theme.danger : Theme.textDim
            }
            Item { width: 1; height: 22 }

            Item {
                width: parent.width; height: 44

                // progress
                Item {
                    anchors.fill: parent
                    opacity: win.st === "working" ? 1 : 0
                    visible: opacity > 0
                    Behavior on opacity { NumberAnimation { duration: 240 } }
                    Rectangle {
                        id: track
                        anchors.verticalCenter: parent.verticalCenter
                        width: parent.width; height: 8; radius: 4
                        color: Theme.card
                        Rectangle {
                            height: parent.height; radius: 4
                            width: Math.max(height, parent.width * installer.progress)
                            Behavior on width { SmoothedAnimation { velocity: 900 } }
                            gradient: Gradient {
                                orientation: Gradient.Horizontal
                                GradientStop { position: 0; color: Theme.accentA }
                                GradientStop { position: 1; color: Theme.accentB }
                            }
                            clip: true
                            Rectangle {   // shimmer
                                width: 80; height: parent.height
                                gradient: Gradient {
                                    orientation: Gradient.Horizontal
                                    GradientStop { position: 0; color: "transparent" }
                                    GradientStop { position: 0.5; color: Qt.rgba(1, 1, 1, 0.45) }
                                    GradientStop { position: 1; color: "transparent" }
                                }
                                NumberAnimation on x { from: -80; to: track.width; duration: 1300; loops: Animation.Infinite }
                            }
                        }
                    }
                }

                // buttons
                Row {
                    anchors.centerIn: parent
                    spacing: 12
                    opacity: win.st === "working" ? 0 : 1
                    visible: opacity > 0
                    Behavior on opacity { NumberAnimation { duration: 240 } }
                    PillButton {
                        visible: win.st !== "done"
                        primary: true; h: 44; hpad: 26; fontSize: 15
                        icon: win.st === "error" ? Icons.restore : Icons.arrowDown
                        text: win.st === "error" ? "Try again" : "Install"
                        onClicked: installer.start()
                    }
                    PillButton {
                        visible: win.st === "done"
                        primary: true; h: 44; hpad: 26; fontSize: 15
                        icon: Icons.play; text: "Open Lumen"
                        onClicked: installer.launch()
                    }
                    PillButton {
                        visible: win.st === "done"
                        h: 44; hpad: 22; fontSize: 15
                        text: "Close"
                        onClicked: Qt.quit()
                    }
                }
            }
            Item { width: 1; height: 14 }
            Text {
                width: parent.width
                horizontalAlignment: Text.AlignHCenter
                elide: Text.ElideMiddle
                text: win.st === "done" ? (installer.updating ? "Reopening Lumen…" : "Right-click any video → Open with → Lumen Player") : installer.target
                font.family: Theme.font; font.pixelSize: 11
                color: Theme.textFaint
            }
        }
    }
}
