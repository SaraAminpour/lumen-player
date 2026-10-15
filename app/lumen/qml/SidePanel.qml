import QtQuick
import QtQuick.Controls.Basic
import "."
import "Icons.js" as Icons

// Right-hand drawer with animated tab indicator; pages slide/fade on switch.
Item {
    id: panel
    property Item blurSource: null
    property string page: "subs"
    property real shift: 0
    readonly property bool hovered: hh.hovered
    readonly property var tabs: [
        { key: "subs", label: "Subtitles", icon: Icons.subtitlesOutline },
        { key: "audio", label: "Audio", icon: Icons.musicNoteOutline },
        { key: "video", label: "Video", icon: Icons.tuneVariant },
        { key: "playlist", label: "Playlist", icon: Icons.playlistPlay },
        { key: "info", label: "Info", icon: Icons.informationOutline },
        { key: "settings", label: "Settings", icon: Icons.cogOutline }
    ]
    readonly property int pageIndex: {
        for (var i = 0; i < tabs.length; i++) if (tabs[i].key === page) return i
        return 0
    }

    HoverHandler { id: hh }
    // swallow clicks so they don't reach the video
    MouseArea { anchors.fill: parent; acceptedButtons: Qt.AllButtons; onWheel: (w) => w.accepted = true }

    Glass {
        anchors.fill: parent
        radius: 24
        tint: 0.72
        source: panel.blurSource
        sx: panel.x + panel.shift
        sy: panel.y
    }

    // header
    Item {
        id: header
        x: 16; y: 14
        width: parent.width - 32
        height: 34
        Text {
            text: panel.tabs[panel.pageIndex].label
            color: Theme.text
            font.family: Theme.font; font.pixelSize: 20; font.weight: Font.Bold
            anchors.verticalCenter: parent.verticalCenter
        }
        IconButton {
            anchors.right: parent.right
            anchors.verticalCenter: parent.verticalCenter
            size: 32; iconSize: 18
            icon: Icons.close
            tip: "Close panel (Esc)"
            onClicked: win.panel = ""
        }
    }

    // tabs
    Item {
        id: tabBar
        x: 12; y: header.y + header.height + 10
        width: parent.width - 24
        height: 54
        readonly property real tabW: width / panel.tabs.length
        Rectangle {
            anchors.fill: parent
            radius: 16
            color: Qt.rgba(0, 0, 0, 0.25)
        }
        Rectangle {
            id: indicator
            y: 4
            height: parent.height - 8
            width: tabBar.tabW - 8
            x: panel.pageIndex * tabBar.tabW + 4
            Behavior on x { NumberAnimation { duration: 420; easing.type: Easing.OutBack; easing.overshoot: 0.9 } }
            radius: 12
            gradient: Gradient {
                orientation: Gradient.Horizontal
                GradientStop { position: 0; color: Qt.rgba(Theme.accentA.r, Theme.accentA.g, Theme.accentA.b, 0.42) }
                GradientStop { position: 1; color: Qt.rgba(Theme.accentB.r, Theme.accentB.g, Theme.accentB.b, 0.30) }
            }
            border.color: Qt.rgba(1, 1, 1, 0.12)
        }
        Row {
            anchors.fill: parent
            Repeater {
                model: panel.tabs
                delegate: Item {
                    required property var modelData
                    required property int index
                    width: tabBar.tabW
                    height: tabBar.height
                    readonly property bool on: panel.pageIndex === index
                    Column {
                        anchors.centerIn: parent
                        spacing: 3
                        Icon {
                            anchors.horizontalCenter: parent.horizontalCenter
                            path: modelData.icon
                            size: 20
                            color: on ? Theme.text : tma.containsMouse ? Theme.text : Theme.textDim
                            scale: tma.pressed ? 0.85 : on ? 1.06 : 1
                            Behavior on scale { NumberAnimation { duration: 240; easing.type: Easing.OutBack } }
                        }
                        Text {
                            anchors.horizontalCenter: parent.horizontalCenter
                            text: modelData.label
                            color: on ? Theme.text : Theme.textFaint
                            font.family: Theme.font; font.pixelSize: 10; font.weight: on ? Font.DemiBold : Font.Medium
                        }
                    }
                    MouseArea { id: tma; anchors.fill: parent; hoverEnabled: true; cursorShape: Qt.PointingHandCursor; onClicked: panel.page = modelData.key }
                }
            }
        }
    }

    // pages
    Item {
        id: pages
        x: 0
        y: tabBar.y + tabBar.height + 12
        width: parent.width
        height: parent.height - y - 8
        clip: true

        Repeater {
            model: panel.tabs
            delegate: Flickable {
                id: fl
                required property var modelData
                required property int index
                readonly property bool current: panel.pageIndex === index
                width: pages.width
                height: pages.height
                contentWidth: width
                contentHeight: loader.height + 24
                boundsBehavior: Flickable.StopAtBounds
                interactive: current
                visible: opacity > 0.01
                opacity: current ? 1 : 0
                Behavior on opacity { NumberAnimation { duration: 240; easing.type: Easing.OutCubic } }
                transform: Translate {
                    x: fl.current ? 0 : (fl.index < panel.pageIndex ? -36 : 36)
                    Behavior on x { NumberAnimation { duration: 380; easing.type: Easing.OutCubic } }
                }
                ScrollBar.vertical: ScrollBar {
                    policy: ScrollBar.AsNeeded
                    width: 8
                    contentItem: Rectangle { implicitWidth: 4; radius: 2; color: Qt.rgba(1, 1, 1, 0.25) }
                }
                Loader {
                    id: loader
                    x: 16
                    width: fl.width - 32
                    active: fl.current || item !== null
                    source: {
                        switch (fl.modelData.key) {
                        case "subs": return "SubtitlePage.qml"
                        case "audio": return "AudioPage.qml"
                        case "video": return "VideoPage.qml"
                        case "playlist": return "PlaylistPage.qml"
                        case "info": return "InfoPage.qml"
                        default: return "SettingsPage.qml"
                        }
                    }
                }
            }
        }
    }
}
