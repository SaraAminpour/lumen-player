import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
import "."
import "Icons.js" as Icons

Popup {
    id: pop
    width: 300
    padding: 16
    modal: false
    closePolicy: Popup.CloseOnEscape | Popup.CloseOnPressOutsideParent
    background: Rectangle { radius: 18; color: "#151821"; border.color: Theme.glassBorder }
    enter: Transition {
        ParallelAnimation {
            NumberAnimation { property: "opacity"; from: 0; to: 1; duration: 160 }
            NumberAnimation { property: "scale"; from: 0.9; to: 1; duration: 260; easing.type: Easing.OutBack }
        }
    }
    exit: Transition {
        ParallelAnimation {
            NumberAnimation { property: "opacity"; to: 0; duration: 120 }
            NumberAnimation { property: "scale"; to: 0.95; duration: 120 }
        }
    }
    contentItem: Column {
        spacing: 12
        Row {
            width: parent.width
            Text { text: "Playback speed"; color: Theme.text; font.family: Theme.font; font.pixelSize: 14; font.weight: Font.DemiBold; width: parent.width - val.width }
            Text { id: val; text: player.speed.toFixed(2) + "×"; color: Theme.accent; font.family: Theme.font; font.pixelSize: 14; font.weight: Font.Bold; font.features: { "tnum": 1 } }
        }
        GridLayout {
            width: parent.width
            columns: 5
            rowSpacing: 6; columnSpacing: 6
            Repeater {
                model: [0.25, 0.5, 0.75, 1, 1.25, 1.5, 1.75, 2, 2.5, 3]
                delegate: Chip {
                    Layout.fillWidth: true
                    text: modelData + "×"
                    checked: Math.abs(player.speed - modelData) < 0.001
                    onClicked: { player.setProp("speed", modelData); toast.show("Speed " + modelData + "×", Icons.playSpeed) }
                }
            }
        }
        LSlider {
            width: parent.width
            from: 0.25; to: 4; stepSize: 0.05; defaultValue: 1
            value: player.speed
            onMoved: (v) => player.setProp("speed", v)
        }
        LSwitch {
            width: parent.width
            text: "Keep voice pitch"
            hint: "Avoids chipmunk voices at higher speeds"
            checked: prefs.pitchCorrection
            onToggled: (on) => { prefs.pitchCorrection = on; player.setProp("audio-pitch-correction", on) }
        }
    }
}
