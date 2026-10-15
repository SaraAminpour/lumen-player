import QtQuick
import "."
import "Icons.js" as Icons

Column {
    id: page
    spacing: 12

    Flow {
        width: parent.width
        spacing: 8
        PillButton { icon: Icons.filePlusOutline; text: "Add files"; h: 34; fontSize: 12; onClicked: { openDialog.appendMode = true; openDialog.open() } }
        PillButton { icon: Icons.folderOutline; text: "Add folder"; h: 34; fontSize: 12; onClicked: { folderDialog.appendMode = true; folderDialog.open() } }
        PillButton { icon: Icons.shuffleVariant; text: "Shuffle"; h: 34; fontSize: 12; enabled: player.playlist.length > 2; onClicked: { player.command(["playlist-shuffle"]); toast.show("Playlist shuffled", Icons.shuffleVariant) } }
        PillButton {
            icon: Icons.repeat; text: "Loop all"; h: 34; fontSize: 12
            checked: player.loopPlaylist !== false && player.loopPlaylist !== "no"
            onClicked: player.setProp("loop-playlist", checked ? "no" : "inf")
        }
        PillButton {
            icon: Icons.repeatOnce; text: "Loop file"; h: 34; fontSize: 12
            checked: player.loopFile !== false && player.loopFile !== "no"
            onClicked: win.toggleLoopFile()
        }
        PillButton { icon: Icons.trashCanOutline; text: "Clear others"; h: 34; fontSize: 12; enabled: player.playlist.length > 1; onClicked: player.command(["playlist-clear"]) }
    }

    Text {
        visible: player.playlist.length === 0
        width: parent.width
        wrapMode: Text.WordWrap
        text: "Nothing queued. Open a file and the rest of its folder is added automatically, or drop several files on the window."
        color: Theme.textFaint; font.family: Theme.font; font.pixelSize: 12
    }

    Column {
        width: parent.width
        spacing: 4
        Repeater {
            model: player.playlist
            delegate: Rectangle {
                id: row
                required property var modelData
                required property int index
                readonly property bool cur: !!modelData.current
                width: parent.width
                height: 48
                radius: 12
                color: cur ? Qt.rgba(Theme.accentA.r, Theme.accentA.g, Theme.accentA.b, 0.16) : rma.containsMouse ? Theme.cardHover : "transparent"
                border.color: cur ? Qt.rgba(Theme.accentA.r, Theme.accentA.g, Theme.accentA.b, 0.45) : "transparent"
                Behavior on color { ColorAnimation { duration: Theme.fast } }
                opacity: 0
                Component.onCompleted: appear.start()
                NumberAnimation on opacity { id: appear; running: false; from: 0; to: 1; duration: 300 + Math.min(row.index, 12) * 30; easing.type: Easing.OutCubic }

                Item {
                    id: lead
                    x: 10; width: 26; height: parent.height
                    Text {
                        visible: !row.cur
                        anchors.centerIn: parent
                        text: row.index + 1
                        color: Theme.textFaint; font.family: Theme.font; font.pixelSize: 12; font.features: { "tnum": 1 }
                    }
                    Row {   // animated "now playing" bars
                        visible: row.cur
                        anchors.centerIn: parent
                        spacing: 2
                        Repeater {
                            model: 3
                            delegate: Rectangle {
                                required property int index
                                width: 3; radius: 1.5
                                color: Theme.accent
                                anchors.bottom: parent.bottom
                                height: 6
                                SequentialAnimation on height {
                                    running: row.cur && !player.paused
                                    loops: Animation.Infinite
                                    NumberAnimation { to: 14; duration: 260 + index * 90; easing.type: Easing.InOutSine }
                                    NumberAnimation { to: 4; duration: 300 + index * 70; easing.type: Easing.InOutSine }
                                }
                            }
                        }
                    }
                }
                Column {
                    anchors.left: lead.right; anchors.leftMargin: 8
                    anchors.right: rm.left; anchors.rightMargin: 6
                    anchors.verticalCenter: parent.verticalCenter
                    spacing: 2
                    Text {
                        width: parent.width
                        text: row.modelData.title || bridge.baseName(row.modelData.filename)
                        elide: Text.ElideMiddle
                        color: Theme.text; font.family: Theme.font; font.pixelSize: 13; font.weight: row.cur ? Font.DemiBold : Font.Normal
                    }
                    Text {
                        width: parent.width
                        text: bridge.dirName(row.modelData.filename)
                        elide: Text.ElideLeft
                        visible: text !== ""
                        color: Theme.textFaint; font.family: Theme.font; font.pixelSize: 10
                    }
                }
                MouseArea {
                    id: rma
                    anchors.fill: parent
                    hoverEnabled: true
                    cursorShape: Qt.PointingHandCursor
                    onClicked: player.setProp("playlist-pos", row.index)
                }
                IconButton {
                    id: rm
                    anchors.right: parent.right; anchors.rightMargin: 6
                    anchors.verticalCenter: parent.verticalCenter
                    size: 30; iconSize: 16
                    icon: Icons.close
                    opacity: rma.containsMouse || hovered ? 1 : 0
                    Behavior on opacity { NumberAnimation { duration: Theme.fast } }
                    tip: "Remove"
                    onClicked: player.command(["playlist-remove", row.index])
                }
            }
        }
    }
}
