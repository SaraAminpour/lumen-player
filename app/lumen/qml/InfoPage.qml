import QtQuick
import "."
import "Icons.js" as Icons

Column {
    id: page
    spacing: 12
    readonly property var vp: player.videoParams || ({})
    readonly property var ap: player.audioParams || ({})

    component KV: Item {
        property string k: ""
        property string v: ""
        width: parent ? parent.width : 300
        height: Math.max(22, vt.implicitHeight + 4)
        visible: v !== "" && v !== "—"
        Text { id: kt; text: parent.k; color: Theme.textFaint; font.family: Theme.font; font.pixelSize: 12; width: 110 }
        Text { id: vt; x: 116; width: parent.width - 116; text: parent.v; color: Theme.text; font.family: Theme.font; font.pixelSize: 12; wrapMode: Text.Wrap; font.features: { "tnum": 1 } }
    }

    Section {
        title: "File"; icon: Icons.fileVideoOutline
        KV { k: "Name"; v: player.fileName }
        KV { k: "Folder"; v: bridge.dirName(player.path) }
        KV { k: "Size"; v: Theme.fmtBytes(player.fileSize) }
        KV { k: "Container"; v: player.fileFormat }
        KV { k: "Duration"; v: player.duration > 0 ? Theme.fmtTime(player.duration) : "" }
        KV { k: "Chapters"; v: player.chapters.length > 0 ? "" + player.chapters.length : "" }
        Flow {
            width: parent.width; spacing: 8
            PillButton { icon: Icons.folderOpenOutline; text: "Show in folder"; h: 32; fontSize: 12; enabled: player.path !== ""; onClicked: bridge.reveal(player.path) }
            PillButton { icon: Icons.linkVariant; text: "Copy path"; h: 32; fontSize: 12; enabled: player.path !== ""; onClicked: { bridge.copyText(player.path); toast.show("Path copied", Icons.check) } }
        }
    }
    Section {
        title: "Video"; icon: Icons.movieOpenOutline
        KV { k: "Codec"; v: player.videoCodec }
        KV { k: "Resolution"; v: page.vp.w ? page.vp.w + " × " + page.vp.h : "" }
        KV { k: "Frame rate"; v: player.fps > 0 ? player.fps.toFixed(3).replace(/\.?0+$/, "") + " fps" : "" }
        KV { k: "Bitrate"; v: Theme.fmtRate(player.videoBitrate) }
        KV { k: "Pixel format"; v: page.vp.pixelformat || "" }
        KV { k: "Colour"; v: page.vp.colormatrix ? [page.vp.colormatrix, page.vp.primaries, page.vp.gamma].join(" · ") : "" }
        KV { k: "Decoder"; v: player.hwdecCurrent && player.hwdecCurrent !== "no" ? "Hardware (" + player.hwdecCurrent + ")" : (page.vp.w ? "Software" : "") }
        KV { k: "Dropped"; v: page.vp.w ? player.frameDrops + " frames" : "" }
    }
    Section {
        title: "Audio"; icon: Icons.waveform
        KV { k: "Codec"; v: player.audioCodec }
        KV { k: "Channels"; v: page.ap["hr-channels"] || page.ap.channels || "" }
        KV { k: "Sample rate"; v: page.ap.samplerate ? (page.ap.samplerate / 1000) + " kHz" : "" }
        KV { k: "Bitrate"; v: Theme.fmtRate(player.audioBitrate) }
    }
}
