import QtQuick
import "."
import "Icons.js" as Icons

Modal {
    id: sheet
    cardWidth: 860
    cardHeight: 600
    readonly property var groups: [
        ["Playback", [["Space / K", "Play · pause"], ["← / →", "Seek 5 s"], ["Shift + ← / →", "Seek 1 s"], ["J / L", "Seek 10 s"], [", / .", "Frame back · forward"], ["[ / ]", "Speed − / +"], ["Backspace", "Normal speed"], ["0 – 9", "Jump to 0–90 %"], ["N / P", "Next · previous file"], ["PgUp / PgDn", "Chapters"], ["B", "A-B loop"], ["Ctrl + R", "Loop file"]]],
        ["Subtitles", [["Z / X", "Delay −/+ 100 ms"], ["Shift + Z / X", "Delay −/+ 1 s"], ["G / H", "Snap to prev · next line"], ["C", "Next subtitle track"], ["V", "Show · hide"], ["Ctrl + = / −", "Bigger · smaller"], ["R / Shift + R", "Move up · down"], ["Ctrl + Shift + S", "Load subtitle file"]]],
        ["Audio & window", [["↑ / ↓  ·  wheel", "Volume"], ["M", "Mute"], ["− / =", "Audio delay −/+ 100 ms"], ["A", "Next audio track"], ["F · double-click", "Full screen"], ["T", "Always on top"], ["S", "Screenshot"], ["I", "Media info"], ["Ctrl + O / U", "Open file · URL"], ["Ctrl + 1 – 5", "Panels"], ["? / F1", "This sheet"], ["Esc", "Close · exit full screen"]]]
    ]
    Item {
        anchors.fill: parent
        anchors.margins: 28
        Row {
            id: head
            spacing: 10
            Icon { path: Icons.keyboardOutline; size: 24; color: Theme.accent; anchors.verticalCenter: parent.verticalCenter }
            Text { text: "Keyboard shortcuts"; color: Theme.text; font.family: Theme.font; font.pixelSize: 20; font.weight: Font.Bold }
        }
        IconButton { anchors.right: parent.right; y: -4; size: 34; iconSize: 18; icon: Icons.close; onClicked: sheet.open = false }
        Row {
            anchors.top: head.bottom
            anchors.topMargin: 20
            width: parent.width
            spacing: 24
            Repeater {
                model: sheet.groups
                delegate: Column {
                    required property var modelData
                    required property int index
                    width: (parent.width - 48) / 3
                    spacing: 6
                    opacity: sheet.open ? 1 : 0
                    Behavior on opacity { NumberAnimation { duration: 300 + index * 120 } }
                    Text { text: modelData[0].toUpperCase(); color: Theme.accent; font.family: Theme.font; font.pixelSize: 11; font.weight: Font.Bold; font.letterSpacing: 1.2; bottomPadding: 4 }
                    Repeater {
                        model: modelData[1]
                        delegate: Item {
                            required property var modelData
                            width: parent.width; height: 30
                            Rectangle {
                                id: key
                                height: 24; radius: 7
                                width: kt.implicitWidth + 14
                                anchors.verticalCenter: parent.verticalCenter
                                color: Qt.rgba(1, 1, 1, 0.08)
                                border.color: Qt.rgba(1, 1, 1, 0.14)
                                Text { id: kt; anchors.centerIn: parent; text: modelData[0]; color: Theme.text; font.family: Theme.font; font.pixelSize: 11; font.weight: Font.DemiBold }
                            }
                            Text {
                                anchors.left: key.right; anchors.leftMargin: 10
                                anchors.right: parent.right
                                anchors.verticalCenter: parent.verticalCenter
                                text: modelData[1]; elide: Text.ElideRight
                                color: Theme.textDim; font.family: Theme.font; font.pixelSize: 12
                            }
                        }
                    }
                }
            }
        }
    }
}
