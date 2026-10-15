import QtQuick
import "."
import "Icons.js" as Icons

// Mute button whose slider slides open on hover.
Item {
    id: root
    readonly property bool open: hh.hovered || slider.pressed
    readonly property real vol: player.volume
    readonly property real maxVol: prefs.volumeBoost ? 200 : 100
    implicitWidth: btn.width + (open ? 112 : 0)
    implicitHeight: 40
    width: implicitWidth
    height: 40
    clip: true
    Behavior on implicitWidth { NumberAnimation { duration: 300; easing.type: Easing.OutCubic } }

    HoverHandler { id: hh }

    IconButton {
        id: btn
        icon: player.muted || root.vol <= 0 ? Icons.volumeOff : root.vol < 34 ? Icons.volumeLow : root.vol < 67 ? Icons.volumeMedium : Icons.volumeHigh
        tip: player.muted ? "Unmute (M)" : "Mute (M)"
        onClicked: player.cycle("mute")
    }
    LSlider {
        id: slider
        anchors.left: btn.right
        anchors.leftMargin: 6
        anchors.verticalCenter: parent.verticalCenter
        width: 76
        opacity: root.open ? 1 : 0
        Behavior on opacity { NumberAnimation { duration: 200 } }
        from: 0; to: root.maxVol; stepSize: 1
        value: player.muted ? 0 : root.vol
        warnFrom: 100; warnAbove: Theme.warn
        defaultValue: 100
        onMoved: (v) => { if (player.muted) player.setProp("mute", false); player.setProp("volume", v); toast.show(Math.round(v) + "%", Icons.volumeHigh, v / root.maxVol) }
    }
    Text {
        anchors.left: slider.right
        anchors.leftMargin: 8
        anchors.verticalCenter: parent.verticalCenter
        opacity: root.open ? 1 : 0
        Behavior on opacity { NumberAnimation { duration: 200 } }
        text: player.muted ? "Muted" : Math.round(root.vol)
        color: root.vol > 100 ? Theme.warn : Theme.textDim
        font.family: Theme.font; font.pixelSize: 12; font.features: { "tnum": 1 }
    }
}
