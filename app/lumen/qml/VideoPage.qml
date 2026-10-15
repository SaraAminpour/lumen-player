import QtQuick
import QtQuick.Layouts
import "."
import "Icons.js" as Icons

Column {
    id: page
    spacing: 12
    readonly property real aspect: {
        var a = player.aspectOverride
        if (a === "no" || a === undefined || a === null) return -1
        var n = Number(a)
        return isNaN(n) ? -1 : n
    }
    function hasVf(label) {
        var fs = player.vf || []
        for (var i = 0; i < fs.length; i++) if (fs[i].label === label) return true
        return false
    }

    Section {
        title: "Aspect ratio"; icon: Icons.aspectRatio
        Flow {
            width: parent.width
            spacing: 6
            Repeater {
                model: [["Auto", -1], ["16:9", 16 / 9], ["4:3", 4 / 3], ["21:9", 21 / 9], ["2.35:1", 2.35], ["1.85:1", 1.85], ["1:1", 1], ["9:16", 9 / 16]]
                delegate: Chip {
                    required property var modelData
                    text: modelData[0]
                    checked: modelData[1] < 0 ? page.aspect <= 0 : Math.abs(page.aspect - modelData[1]) < 0.01
                    onClicked: {
                        player.setProp("video-aspect-override", modelData[1] < 0 ? "no" : modelData[1].toFixed(6))
                        toast.show("Aspect " + modelData[0], Icons.aspectRatio)
                    }
                }
            }
        }
        LSwitch {
            width: parent.width
            text: "Fill the window (crop black bars)"
            checked: prefs.fillCrop
            onToggled: (on) => { prefs.fillCrop = on; player.setProp("panscan", on ? 1.0 : 0.0) }
        }
    }

    Section {
        title: "Zoom & rotate"; icon: Icons.magnifyPlusOutline
        Text { text: "Zoom  ·  " + Math.round(Math.pow(2, player.zoom) * 100) + "%"; color: Theme.textDim; font.family: Theme.font; font.pixelSize: 12 }
        LSlider {
            width: parent.width; from: -1; to: 1.5; stepSize: 0.01; defaultValue: 0; centered: true
            value: player.zoom
            onMoved: (v) => player.setProp("video-zoom", v)
        }
        Flow {
            width: parent.width
            spacing: 8
            PillButton { icon: Icons.rotateLeft; text: "Rotate"; h: 34; fontSize: 12; onClicked: player.setProp("video-rotate", (player.rotate + 270) % 360) }
            PillButton { icon: Icons.rotateRight; text: "Rotate"; h: 34; fontSize: 12; onClicked: player.setProp("video-rotate", (player.rotate + 90) % 360) }
            PillButton { icon: Icons.flipHorizontal; text: "Mirror"; checked: page.hasVf("flip"); h: 34; fontSize: 12; onClicked: player.toggleFilter("vf", "@flip:hflip") }
            PillButton {
                icon: Icons.restore; text: "Reset"; h: 34; fontSize: 12
                onClicked: { player.setProp("video-zoom", 0); player.setProp("video-pan-x", 0); player.setProp("video-pan-y", 0); player.setProp("video-rotate", 0) }
            }
        }
    }

    Section {
        title: "Picture"; icon: Icons.brightness6
        trailing: PillButton {
            text: "Reset"; h: 26; hpad: 10; fontSize: 11
            onClicked: { var ks = ["brightness", "contrast", "saturation", "gamma", "hue"]; for (var i = 0; i < ks.length; i++) player.setProp(ks[i], 0) }
        }
        Repeater {
            model: [["Brightness", "brightness"], ["Contrast", "contrast"], ["Saturation", "saturation"], ["Gamma", "gamma"], ["Hue", "hue"]]
            delegate: Column {
                required property var modelData
                width: parent.width
                spacing: 2
                readonly property int v: player[modelData[1]]
                Item {
                    width: parent.width; height: 16
                    Text { text: modelData[0]; color: Theme.textDim; font.family: Theme.font; font.pixelSize: 12 }
                    Text { anchors.right: parent.right; text: (v > 0 ? "+" : "") + v; color: v === 0 ? Theme.textFaint : Theme.accent; font.family: Theme.font; font.pixelSize: 12; font.features: { "tnum": 1 } }
                }
                LSlider {
                    width: parent.width; from: -100; to: 100; stepSize: 1; centered: true; defaultValue: 0
                    value: v
                    onMoved: (val) => player.setProp(modelData[1], Math.round(val))
                }
            }
        }
    }

    Section {
        title: "Quality"; icon: Icons.autoFix
        LSwitch {
            width: parent.width
            text: "Hardware decoding"
            hint: player.hwdecCurrent && player.hwdecCurrent !== "no" ? "Active: " + player.hwdecCurrent : "Using the CPU"
            checked: String(player.hwdec) !== "no"
            onToggled: (on) => { prefs.hwdec = on; player.setProp("hwdec", on ? "auto-safe" : "no") }
        }
        LSwitch {
            width: parent.width
            text: "Debanding"
            hint: "Smooths colour banding in dark scenes and skies"
            checked: player.deband
            onToggled: (on) => player.setProp("deband", on)
        }
        LSwitch {
            width: parent.width
            text: "Deinterlace"
            hint: "For old TV recordings and DVDs with comb lines"
            checked: player.deinterlace === true || player.deinterlace === "yes"
            onToggled: (on) => player.setProp("deinterlace", on)
        }
        LSwitch {
            width: parent.width
            text: "Sharpen"
            hint: "Contrast-adaptive sharpening for soft footage"
            checked: page.hasVf("sharp")
            onToggled: (on) => player.toggleFilter("vf", "@sharp:lavfi=[cas=0.6]")
        }
    }

    Section {
        title: "Screenshot"; icon: Icons.cameraOutline
        Flow {
            width: parent.width
            spacing: 8
            PillButton { icon: Icons.cameraOutline; text: "With subtitles"; h: 34; fontSize: 12; onClicked: win.screenshot("subtitles") }
            PillButton { icon: Icons.filmstrip; text: "Video only"; h: 34; fontSize: 12; onClicked: win.screenshot("video") }
            PillButton { icon: Icons.folderOpenOutline; text: "Open folder"; h: 34; fontSize: 12; onClicked: bridge.openFolder(bridge.screenshotDir()) }
        }
    }
}
