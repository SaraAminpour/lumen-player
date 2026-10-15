import QtQuick
import "."
import "Icons.js" as Icons

Column {
    id: page
    spacing: 12
    function hasFilter(label) {
        var fs = player.af || []
        for (var i = 0; i < fs.length; i++) if (fs[i].label === label) return true
        return false
    }

    Section {
        title: "Track"; icon: Icons.musicNoteOutline
        TrackList {
            type: "audio"
            selected: player.aid
            offText: "No audio"
            onPick: (id) => player.setProp("aid", id)
        }
        PillButton { icon: Icons.filePlusOutline; text: "Load audio file…"; h: 34; fontSize: 12; onClicked: audioDialog.open() }
    }

    Section {
        title: "Audio sync"; icon: Icons.timerOutline
        SyncControl {
            value: player.audioDelay
            earlyText: "earlier (sound plays sooner)"
            lateText: "later (sound plays later)"
            onSetTo: (v) => { player.setProp("audio-delay", v); toast.show("Audio delay " + Theme.fmtDelay(v), Icons.waveform) }
        }
        Text {
            width: parent.width; wrapMode: Text.WordWrap
            text: "Lips move before you hear the voice? Make audio <b>earlier</b>. Shortcuts: <b>−</b> / <b>=</b>."
            textFormat: Text.StyledText
            color: Theme.textFaint; font.family: Theme.font; font.pixelSize: 11
        }
    }

    Section {
        title: "Sound"; icon: Icons.volumeHigh
        Text { text: "Volume  ·  " + Math.round(player.volume) + "%"; color: player.volume > 100 ? Theme.warn : Theme.textDim; font.family: Theme.font; font.pixelSize: 12 }
        LSlider {
            width: parent.width
            from: 0; to: prefs.volumeBoost ? 200 : 100; stepSize: 1; defaultValue: 100
            warnFrom: 100; warnAbove: Theme.warn
            value: player.volume
            onMoved: (v) => player.setProp("volume", v)
        }
        LSwitch {
            width: parent.width
            text: "Volume boost up to 200%"
            hint: "For quiet files. Very high values can distort"
            checked: prefs.volumeBoost
            onToggled: (on) => { prefs.volumeBoost = on; if (!on && player.volume > 100) player.setProp("volume", 100) }
        }
        LSwitch {
            width: parent.width
            text: "Normalize loudness"
            hint: "Evens out quiet dialogue and loud explosions"
            checked: page.hasFilter("norm")
            onToggled: (on) => player.toggleFilter("af", "@norm:lavfi=[dynaudnorm=f=250:g=31:p=0.5]")
        }
        LSwitch {
            width: parent.width
            text: "Downmix to stereo"
            hint: "Better dialogue on headphones for 5.1 / 7.1 audio"
            checked: prefs.downmix
            onToggled: (on) => { prefs.downmix = on; player.setProp("audio-channels", on ? "stereo" : "auto-safe") }
        }
    }

    Section {
        title: "Output device"; icon: Icons.speaker
        Repeater {
            model: player.audioDevices
            delegate: TrackRow {
                required property var modelData
                width: parent.width
                title: modelData.description || modelData.name
                sub: modelData.name === "auto" ? "Follows the Windows default device" : ""
                on: player.audioDevice === modelData.name
                onHit: { player.setProp("audio-device", modelData.name); toast.show(title, Icons.speaker) }
            }
        }
    }
}
