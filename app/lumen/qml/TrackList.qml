import QtQuick
import "."
import "Icons.js" as Icons

// Selectable list of mpv tracks of one type ("sub", "audio", "video").
Column {
    id: root
    property string type: "sub"
    property var selected: false          // mpv sid/aid value (number or false/"no")
    property bool allowOff: true
    property string offText: "Off"
    property var exclude: false           // e.g. the primary sid when picking a secondary one
    signal pick(var id)
    width: parent ? parent.width : 300
    spacing: 4

    readonly property var items: {
        var out = []
        var ts = player.tracks || []
        for (var i = 0; i < ts.length; i++)
            if (ts[i].type === root.type) out.push(ts[i])
        return out
    }

    function describe(t) {
        var bits = []
        if (t.lang) bits.push(t.lang.toUpperCase())
        if (t.codec) bits.push(String(t.codec).toUpperCase())
        if (t["demux-channel-count"]) bits.push(t["demux-channel-count"] + " ch")
        if (t["demux-samplerate"]) bits.push((t["demux-samplerate"] / 1000) + " kHz")
        if (t["demux-w"]) bits.push(t["demux-w"] + "×" + t["demux-h"])
        if (t.forced) bits.push("Forced")
        if (t["default"]) bits.push("Default")
        if (t.external) bits.push("External")
        return bits.join(" · ")
    }
    function isOn(id) { return root.selected !== false && root.selected !== "no" && Number(root.selected) === Number(id) }

    TrackRow {
        width: root.width
        visible: root.allowOff
        title: root.offText
        on: root.selected === false || root.selected === "no"
        onHit: root.pick("no")
    }
    Repeater {
        model: root.items
        delegate: TrackRow {
            width: root.width
            required property var modelData
            visible: !(root.exclude !== false && root.exclude !== "no" && Number(root.exclude) === Number(modelData.id))
            title: modelData.title ? modelData.title : (root.type === "sub" ? "Subtitle " : root.type === "audio" ? "Audio " : "Video ") + modelData.id
            sub: root.describe(modelData)
            on: root.isOn(modelData.id)
            onHit: root.pick(modelData.id)
        }
    }
    Text {
        visible: root.items.length === 0
        text: root.type === "sub" ? "This file has no subtitle tracks yet." : "No tracks."
        color: Theme.textFaint; font.family: Theme.font; font.pixelSize: 12
        leftPadding: 4; topPadding: 4; bottomPadding: 4
    }
}
