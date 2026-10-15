import QtQuick
import QtQuick.Layouts
import "."
import "Icons.js" as Icons

Column {
    id: page
    spacing: 12
    property real markShown: -1     // time the line appeared on screen
    property real markHeard: -1     // time the line was actually spoken

    function applyAssistant() {
        if (markShown < 0 || markHeard < 0) return
        var d = Math.round((markHeard - markShown) * 1000) / 1000
        var v = Math.round((player.subDelay + d) * 1000) / 1000
        player.setProp("sub-delay", v)
        toast.show("Subtitles shifted " + Theme.fmtDelay(d) + "  →  " + Theme.fmtDelay(v), Icons.sync)
        markShown = -1; markHeard = -1
    }

    Section {
        title: "Track"; icon: Icons.subtitlesOutline
        TrackList {
            type: "sub"
            selected: player.sid
            onPick: (id) => { player.setProp("sid", id); if (id !== "no") player.setProp("sub-visibility", true) }
        }
        Flow {
            width: parent.width
            spacing: 8
            PillButton { icon: Icons.filePlusOutline; text: "Load subtitle file…"; h: 34; fontSize: 12; onClicked: subDialog.open() }
            PillButton {
                icon: player.subVisible ? Icons.eyeOffOutline : Icons.eyeOutline
                text: player.subVisible ? "Hide" : "Show"
                h: 34; fontSize: 12
                onClicked: player.cycle("sub-visibility")
            }
        }
    }

    Section {
        title: "Timing"; icon: Icons.timerOutline
        SyncControl {
            value: player.subDelay
            earlyText: "earlier (subtitles appear sooner)"
            lateText: "later (subtitles appear later)"
            onSetTo: (v) => { player.setProp("sub-delay", v); toast.show("Subtitle delay " + Theme.fmtDelay(v), Icons.timerOutline) }
        }
        Text {
            width: parent.width
            wrapMode: Text.WordWrap
            text: "Shortcuts: <b>Z</b> / <b>X</b> nudge by 100 ms, <b>Shift+Z</b> / <b>Shift+X</b> by 1 s."
            textFormat: Text.StyledText
            color: Theme.textFaint; font.family: Theme.font; font.pixelSize: 11
        }
    }

    Section {
        title: "Snap to a line"; icon: Icons.sync
        Text {
            width: parent.width
            wrapMode: Text.WordWrap
            text: "Hearing a line whose subtitle is missing or late? Jump the subtitle timing so a neighbouring line shows right now."
            color: Theme.textDim; font.family: Theme.font; font.pixelSize: 12
        }
        RowLayout {
            width: parent.width
            spacing: 8
            PillButton { Layout.fillWidth: true; icon: Icons.chevronLeft; text: "Previous line now"; h: 36; fontSize: 12; tip: "Shortcut: G"; onClicked: win.subStep(-1) }
            PillButton { Layout.fillWidth: true; icon: Icons.chevronRight; text: "Next line now"; h: 36; fontSize: 12; tip: "Shortcut: H"; onClicked: win.subStep(1) }
        }
    }

    Section {
        title: "Sync assistant"; icon: Icons.creationOutline
        Text {
            width: parent.width
            wrapMode: Text.WordWrap
            text: "Tap <b>Line appeared</b> when a subtitle pops up, and <b>Heard it</b> when that line is spoken (any order). Lumen shifts the subtitles by the difference."
            textFormat: Text.StyledText
            color: Theme.textDim; font.family: Theme.font; font.pixelSize: 12
        }
        RowLayout {
            width: parent.width
            spacing: 8
            PillButton {
                Layout.fillWidth: true
                icon: Icons.textBoxOutline
                text: page.markShown >= 0 ? "Appeared  " + Theme.fmtTime(page.markShown) : "Line appeared"
                checked: page.markShown >= 0
                h: 40; fontSize: 12
                onClicked: { page.markShown = player.position; page.applyAssistant() }
            }
            PillButton {
                Layout.fillWidth: true
                icon: Icons.headphones
                text: page.markHeard >= 0 ? "Heard  " + Theme.fmtTime(page.markHeard) : "Heard it"
                checked: page.markHeard >= 0
                h: 40; fontSize: 12
                onClicked: { page.markHeard = player.position; page.applyAssistant() }
            }
        }
    }

    Section {
        title: "Appearance"; icon: Icons.formatSize
        Text { text: "Size  ·  " + Math.round(player.subScale * 100) + "%"; color: Theme.textDim; font.family: Theme.font; font.pixelSize: 12 }
        LSlider {
            width: parent.width; from: 0.4; to: 2.5; stepSize: 0.05; defaultValue: 1
            value: player.subScale
            onMoved: (v) => { player.setProp("sub-scale", v); prefs.subScale = v }
        }
        Text { text: "Position  ·  " + (prefs.subPos >= 100 ? "bottom" : (100 - prefs.subPos) + "% up"); color: Theme.textDim; font.family: Theme.font; font.pixelSize: 12 }
        LSlider {
            width: parent.width; from: 50; to: 100; stepSize: 1; defaultValue: 100
            value: prefs.subPos
            onMoved: (v) => { prefs.subPos = Math.round(v); win.applySubPos() }
        }
        Text { text: "Colour"; color: Theme.textDim; font.family: Theme.font; font.pixelSize: 12 }
        Row {
            spacing: 10
            Repeater {
                model: ["#FFFFFF", "#FFE45C", "#7DF9FF", "#B6FF8A", "#FFB3D9"]
                delegate: Rectangle {
                    required property string modelData
                    width: 28; height: 28; radius: 14
                    color: modelData
                    border.width: prefs.subColor === modelData ? 3 : 0
                    border.color: Theme.accent
                    scale: cma.pressed ? 0.85 : prefs.subColor === modelData ? 1.12 : 1
                    Behavior on scale { NumberAnimation { duration: 260; easing.type: Easing.OutBack; easing.overshoot: 3 } }
                    MouseArea { id: cma; anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: { prefs.subColor = modelData; win.applySubStyle() } }
                }
            }
        }
        LSwitch {
            width: parent.width
            text: "Background box"
            hint: "Dark box behind the text for readability"
            checked: prefs.subBox
            onToggled: (on) => { prefs.subBox = on; win.applySubStyle() }
        }
        LSwitch {
            width: parent.width
            text: "Override styled (ASS) subtitles"
            hint: "Apply your size and colour to anime-style subtitles too"
            checked: prefs.subOverride
            onToggled: (on) => { prefs.subOverride = on; win.applySubStyle() }
        }
    }

    Section {
        title: "Second subtitle"; icon: Icons.translate
        Text {
            width: parent.width; wrapMode: Text.WordWrap
            text: "Show another language at the top of the screen at the same time — great for learning."
            color: Theme.textDim; font.family: Theme.font; font.pixelSize: 12
        }
        TrackList {
            type: "sub"
            selected: player.secondarySid
            exclude: player.sid
            offText: "None"
            onPick: (id) => player.setProp("secondary-sid", id)
        }
    }
}
