import QtQuick
import "."
import "Icons.js" as Icons

// Title + window controls; also the drag handle for the frameless window.
Item {
    id: bar
    height: 56
    readonly property bool hovered: hh.hovered

    HoverHandler { id: hh }
    // eats clicks so they don't reach the video; drag moves the window, double-click maximizes
    MouseArea {
        anchors.fill: parent
        property point origin
        onPressed: (m) => origin = Qt.point(m.x, m.y)
        onPositionChanged: (m) => {
            if (pressed && !win.fullscreen && Math.abs(m.x - origin.x) + Math.abs(m.y - origin.y) > 4) {
                if (win.visibility === 4) win.showNormal()
                win.startSystemMove()
            }
        }
        onDoubleClicked: win.toggleMaximize()
    }

    Row {
        anchors.left: parent.left
        anchors.leftMargin: 18
        anchors.verticalCenter: parent.verticalCenter
        spacing: 12
        width: parent.width - controls.width - 40
        Image {
            source: "../assets/lumen.png"
            width: 22; height: 22
            sourceSize: Qt.size(44, 44)
            smooth: true
            anchors.verticalCenter: parent.verticalCenter
        }
        Column {
            anchors.verticalCenter: parent.verticalCenter
            width: parent.width - 40
            spacing: 1
            Text {
                text: player.idle ? "Lumen" : (player.mediaTitle || player.fileName)
                color: Theme.text
                font.family: Theme.font; font.pixelSize: 14; font.weight: Font.DemiBold
                elide: Text.ElideMiddle
                width: parent.width
            }
            Text {
                visible: !player.idle && player.playlist.length > 1
                text: (player.playlistPos + 1) + " of " + player.playlist.length + "  ·  " + bridge.dirName(player.path)
                color: Theme.textFaint
                elide: Text.ElideMiddle
                width: parent.width
                font.family: Theme.font; font.pixelSize: 11
            }
        }
    }

    Row {
        id: controls
        anchors.right: parent.right
        anchors.rightMargin: 10
        anchors.verticalCenter: parent.verticalCenter
        spacing: 2
        IconButton { size: 36; iconSize: 18; icon: Icons.dotsVertical; tip: "Menu"; onClicked: ctxMenu.popupAt(x + controls.x, bar.height) }
        IconButton { size: 36; iconSize: 18; icon: win.pinned ? Icons.pin : Icons.pinOutline; checked: win.pinned; tip: win.pinned ? "Unpin (T)" : "Always on top (T)"; onClicked: win.togglePin() }
        IconButton { size: 36; iconSize: 18; icon: Icons.windowMinimize; tip: "Minimize"; onClicked: win.showMinimized() }
        IconButton { size: 36; iconSize: 18; visible: !win.fullscreen; icon: win.visibility === 4 ? Icons.windowRestore : Icons.windowMaximize; tip: win.visibility === 4 ? "Restore" : "Maximize"; onClicked: win.toggleMaximize() }
        IconButton { size: 36; iconSize: 18; icon: Icons.close; danger: true; tip: "Close"; onClicked: win.close() }
    }
}
