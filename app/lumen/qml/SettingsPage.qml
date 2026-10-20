import QtQuick
import "."
import "Icons.js" as Icons

Column {
    id: page
    spacing: 12
    function qualityName(h) { return h === 0 ? "Best" : h === 2160 ? "4K" : h + "p" }

    Section {
        title: "Look"; icon: Icons.paletteOutline
        Text { text: "Accent  ·  " + Theme.accents[Theme.accentIndex].name; color: Theme.textDim; font.family: Theme.font; font.pixelSize: 12 }
        Row {
            spacing: 12
            Repeater {
                model: Theme.accents
                delegate: Item {
                    required property var modelData
                    required property int index
                    width: 34; height: 34
                    readonly property bool on: Theme.accentIndex === index
                    Rectangle {
                        anchors.centerIn: parent
                        width: 34; height: 34; radius: 17
                        color: "transparent"
                        border.width: 2
                        border.color: on ? "white" : "transparent"
                        scale: on ? 1 : 0.6
                        opacity: on ? 1 : 0
                        Behavior on scale { NumberAnimation { duration: 300; easing.type: Easing.OutBack } }
                        Behavior on opacity { NumberAnimation { duration: 200 } }
                    }
                    Rectangle {
                        anchors.centerIn: parent
                        width: 24; height: 24; radius: 12
                        scale: ama.pressed ? 0.8 : ama.containsMouse ? 1.1 : 1
                        Behavior on scale { NumberAnimation { duration: 240; easing.type: Easing.OutBack; easing.overshoot: 3 } }
                        gradient: Gradient {
                            orientation: Gradient.Horizontal
                            GradientStop { position: 0; color: modelData.a }
                            GradientStop { position: 1; color: modelData.b }
                        }
                    }
                    MouseArea { id: ama; anchors.fill: parent; hoverEnabled: true; cursorShape: Qt.PointingHandCursor; onClicked: { Theme.accentIndex = index; prefs.accent = index } }
                }
            }
        }
        LSwitch {
            width: parent.width
            text: "Frosted glass"
            hint: "Blur the video behind controls. Turn off on slow PCs"
            checked: prefs.glassBlur
            onToggled: (on) => prefs.glassBlur = on
        }
        LSwitch {
            width: parent.width
            text: "Seek-bar previews"
            hint: "Thumbnail of the frame under the cursor"
            checked: prefs.thumbnails
            onToggled: (on) => prefs.thumbnails = on
        }
        LSwitch {
            width: parent.width
            text: "Show remaining time"
            checked: prefs.showRemaining
            onToggled: (on) => prefs.showRemaining = on
        }
    }

    Section {
        title: "Playback"; icon: Icons.playCircleOutline
        LSwitch {
            width: parent.width
            text: "Resume where I left off"
            hint: "Remembers position, tracks and delays per file"
            checked: prefs.resume
            onToggled: (on) => { prefs.resume = on; player.setProp("save-position-on-quit", on); player.setProp("resume-playback", on) }
        }
        LSwitch {
            width: parent.width
            text: "Queue the rest of the folder"
            hint: "Opening an episode adds the next ones automatically"
            checked: prefs.autoPlaylist
            onToggled: (on) => prefs.autoPlaylist = on
        }
        Text { text: "Streaming quality  ·  " + page.qualityName(prefs.streamQuality); color: Theme.textDim; font.family: Theme.font; font.pixelSize: 12 }
        Flow {
            width: parent.width; spacing: 6
            Repeater {
                model: [720, 1080, 1440, 2160, 0]
                delegate: Chip {
                    required property int modelData
                    text: page.qualityName(modelData)
                    checked: prefs.streamQuality === modelData
                    onClicked: { prefs.streamQuality = modelData; player.configure_streaming(modelData) }
                }
            }
        }
        Text {
            width: parent.width; wrapMode: Text.WordWrap
            text: "For YouTube and other web videos. Lower it on slow connections."
            color: Theme.textFaint; font.family: Theme.font; font.pixelSize: 11
        }
        LSwitch {
            width: parent.width
            text: "Pause when minimized"
            checked: prefs.pauseOnMinimize
            onToggled: (on) => prefs.pauseOnMinimize = on
        }
        LSwitch {
            width: parent.width
            text: "Remember volume"
            checked: prefs.rememberVolume
            onToggled: (on) => prefs.rememberVolume = on
        }
    }

    Section {
        visible: bridge.isWindows()
        title: "Windows"; icon: Icons.monitor
        Text {
            width: parent.width; wrapMode: Text.WordWrap
            text: "Add Lumen to the right-click “Open with” menu for videos. To make it the default player, then choose Open with › Choose another app › Lumen › Always."
            color: Theme.textDim; font.family: Theme.font; font.pixelSize: 12
        }
        PillButton {
            icon: Icons.fileVideoOutline; text: "Add to “Open with”"; h: 34; fontSize: 12
            onClicked: {
                var r = bridge.registerOpenWith()
                toast.show(r === "ok" ? "Lumen added to “Open with”" : "Couldn't register: " + r, r === "ok" ? Icons.check : Icons.close)
            }
        }
    }

    Section {
        visible: updater.supported
        title: "Updates"; icon: Icons.update
        Text {
            width: parent.width; wrapMode: Text.WordWrap
            text: {
                var st = updater.state
                if (st === "idle") return "You have Lumen " + updater.current + "."
                if (st === "available") return "Lumen " + updater.latest + " is available. You have " + updater.current + "."
                return updater.message
            }
            color: updater.state === "error" ? Theme.danger : updater.state === "available" ? Theme.text : Theme.textDim
            font.family: Theme.font; font.pixelSize: 12
        }
        Text {
            visible: updater.state === "available" && updater.notes !== ""
            width: parent.width; wrapMode: Text.WordWrap
            text: updater.notes; lineHeight: 1.15
            color: Theme.textDim; font.family: Theme.font; font.pixelSize: 12
        }
        Rectangle {
            visible: updater.state === "downloading"
            width: parent.width; height: 6; radius: 3
            color: Qt.rgba(1, 1, 1, 0.12)
            Rectangle {
                width: Math.max(parent.height, parent.width * updater.progress); height: parent.height; radius: 3
                gradient: Gradient {
                    orientation: Gradient.Horizontal
                    GradientStop { position: 0; color: Theme.accentA }
                    GradientStop { position: 1; color: Theme.accentB }
                }
            }
        }
        Flow {
            width: parent.width; spacing: 8
            PillButton {
                visible: updater.state === "available" || (updater.state === "error" && updater.latest !== "" && updater.latest !== updater.current)
                primary: true; icon: Icons.cloudDownloadOutline; h: 34; fontSize: 12
                text: "Install " + updater.latest
                onClicked: updater.install()
            }
            PillButton {
                visible: updater.state !== "downloading" && updater.state !== "checking"
                icon: Icons.update; text: "Check for updates"; h: 34; fontSize: 12
                onClicked: updater.check(false)
            }
            PillButton { icon: Icons.openInNew; text: "Release notes"; h: 34; fontSize: 12; onClicked: Qt.openUrlExternally(updater.releasesPage) }
        }
        LSwitch {
            width: parent.width
            text: "Check for updates automatically"
            hint: "Once a day. Nothing is installed until you press Install"
            checked: prefs.autoUpdate
            onToggled: (on) => prefs.autoUpdate = on
        }
    }

    Section {
        title: "Help"; icon: Icons.keyboardOutline
        Flow {
            width: parent.width; spacing: 8
            PillButton { icon: Icons.keyboardOutline; text: "Keyboard shortcuts"; h: 34; fontSize: 12; onClicked: shortcutsSheet.open = true }
            PillButton { icon: Icons.fileDocumentOutline; text: "Open log"; h: 34; fontSize: 12; onClicked: bridge.openFolder(bridge.dirName(logPath)) }
        }
        Text {
            width: parent.width; wrapMode: Text.WordWrap
            text: "Lumen " + bridge.version() + "  ·  powered by " + (player.getProp("mpv-version") || "mpv") + "  ·  " + (player.getProp("ffmpeg-version") ? "FFmpeg " + player.getProp("ffmpeg-version") : "")
            color: Theme.textFaint; font.family: Theme.font; font.pixelSize: 11
        }
    }
}
