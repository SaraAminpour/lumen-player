import QtQuick
import QtQuick.Layouts
import "."
import "Icons.js" as Icons

// Floating bottom control dock.
Item {
    id: dock
    property Item blurSource: null
    property real shift: 0
    readonly property bool hovered: hh.hovered
    readonly property bool speedOpen: speedPop.opened
    readonly property bool compact: width < 760
    readonly property bool tiny: width < 560
    height: 108

    HoverHandler { id: hh }

    function openSpeed() { speedPop.open() }
    function simulateHover() { seek.demo = true; seek.hoverX = seek.width * 0.62; seek.requestThumb(seek.timeAt(seek.hoverX)) }

    MouseArea { anchors.fill: parent; acceptedButtons: Qt.AllButtons; onWheel: (w) => w.accepted = true }

    Glass {
        anchors.fill: parent
        radius: 22
        source: dock.blurSource
        sx: dock.x
        sy: dock.y + dock.shift
    }

    SeekBar {
        id: seek
        x: 18; y: 10
        width: parent.width - 36
    }

    RowLayout {
        id: bottomRow
        x: 12
        width: parent.width - 24
        y: 46
        height: 52
        spacing: 2

        IconButton { visible: !dock.tiny; icon: Icons.skipPrevious; tip: "Previous (P)"; enabled: player.playlistPos > 0 || player.position > 3; onClicked: win.prevItem() }
        IconButton { icon: Icons.rewind10; tip: "Back 10 s (J)"; onClicked: win.seekRel(-10) }
        PlayButton { Layout.leftMargin: 4; Layout.rightMargin: 4; onClicked: win.togglePause(false) }
        IconButton { icon: Icons.fastForward10; tip: "Forward 10 s (L)"; onClicked: win.seekRel(10) }
        IconButton { visible: !dock.tiny; icon: Icons.skipNext; tip: "Next (N)"; enabled: player.playlistPos < player.playlist.length - 1; onClicked: win.nextItem() }
        VolumeControl { Layout.leftMargin: 4 }

        Item {
            id: timeBox
            Layout.leftMargin: 8
            implicitWidth: timeText.implicitWidth + 12
            Layout.fillHeight: true
            Text {
                id: timeText
                anchors.verticalCenter: parent.verticalCenter
                text: (prefs.showRemaining ? "−" + Theme.fmtTime(player.duration - player.position) : Theme.fmtTime(player.position))
                      + "<font color='#7f8494'>  /  " + Theme.fmtTime(player.duration) + "</font>"
                textFormat: Text.StyledText
                color: Theme.text
                font.family: Theme.font; font.pixelSize: 13; font.weight: Font.Medium
                font.features: { "tnum": 1 }
            }
            MouseArea {
                anchors.fill: parent
                cursorShape: Qt.PointingHandCursor
                onClicked: prefs.showRemaining = !prefs.showRemaining
            }
        }
        Text {
            readonly property var ch: (player.chapters && player.chapter !== null && player.chapter >= 0 && player.chapter < player.chapters.length) ? player.chapters[player.chapter] : null
            visible: !dock.compact && ch !== null
            text: ch ? "•  " + (ch.title || ("Chapter " + (player.chapter + 1))) : ""
            color: Theme.textDim
            elide: Text.ElideRight
            Layout.maximumWidth: 220
            font.family: Theme.font; font.pixelSize: 12
        }

        Item { Layout.fillWidth: true }

        PillButton {
            id: speedBtn
            text: (Math.round(player.speed * 100) / 100) + "×"
            h: 32; hpad: 12; fontSize: 12
            checked: Math.abs(player.speed - 1) > 0.001
            tip: "Playback speed  ([ / ])"
            onClicked: speedPop.opened ? speedPop.close() : speedPop.open()
            SpeedPopup { id: speedPop; y: -height - 64; x: (parent.width - width) / 2 }
        }
        IconButton {
            icon: player.sid !== false && player.sid !== "no" ? Icons.subtitles : Icons.subtitlesOutline
            tip: "Subtitles & sync (Ctrl+1)"
            checked: win.panel === "subs"
            onClicked: win.togglePanel("subs")
        }
        IconButton { icon: Icons.musicNoteOutline; tip: "Audio & sync (Ctrl+2)"; checked: win.panel === "audio"; onClicked: win.togglePanel("audio") }
        IconButton { visible: !dock.tiny; icon: Icons.tuneVariant; tip: "Video adjustments (Ctrl+3)"; checked: win.panel === "video"; onClicked: win.togglePanel("video") }
        IconButton {
            visible: !dock.compact
            icon: Icons.abTesting
            tip: player.abLoopA === "no" ? "A-B loop: set start (B)" : player.abLoopB === "no" ? "A-B loop: set end (B)" : "Clear A-B loop (B)"
            checked: player.abLoopA !== "no"
            onClicked: win.abLoop()
        }
        IconButton { visible: !dock.compact; icon: Icons.cameraOutline; tip: "Screenshot (S)"; onClicked: win.screenshot() }
        IconButton { icon: Icons.playlistPlay; tip: "Playlist (Ctrl+4)"; checked: win.panel === "playlist"; onClicked: win.togglePanel("playlist") }
        IconButton { visible: !dock.tiny; icon: Icons.cogOutline; tip: "Settings & info (Ctrl+5)"; checked: win.panel === "info" || win.panel === "settings"; onClicked: win.togglePanel("settings") }
        IconButton { icon: win.fullscreen ? Icons.fullscreenExit : Icons.fullscreen; tip: win.fullscreen ? "Exit full screen (F)" : "Full screen (F)"; onClicked: win.toggleFullscreen() }
    }
}
