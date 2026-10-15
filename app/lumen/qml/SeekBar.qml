import QtQuick
import QtQuick.Effects
import "."
import "Icons.js" as Icons

// Timeline: buffered range, chapter gaps, A-B loop, hover preview bubble with thumbnail.
Item {
    id: root
    property real position: player.position
    property real duration: player.duration
    property bool demo: false
    readonly property bool active: hover.hovered || drag.pressed || demo
    property real dragPos: 0
    property real hoverX: 0
    readonly property real shownPos: drag.pressed ? dragPos : position
    property string thumbKey: ""
    property real lastThumbT: -1
    height: 30

    function timeAt(x) { return duration > 0 ? Math.max(0, Math.min(1, x / width)) * duration : 0 }
    function xAt(t) { return duration > 0 ? Math.max(0, Math.min(1, t / duration)) * width : 0 }
    function chapterAt(t) {
        var cs = player.chapters || [], title = ""
        for (var i = 0; i < cs.length; i++) if (cs[i].time <= t + 0.01) title = cs[i].title || ("Chapter " + (i + 1))
        return title
    }
    function requestThumb(t) {
        if (!prefs.thumbnails || !!!(player.videoParams && player.videoParams.w)) return
        var step = Math.max(1, duration / 400)
        var q = Math.round(t / step) * step
        if (Math.abs(q - lastThumbT) < 0.01) return
        lastThumbT = q
        thumbs.request(player.path, q)
    }
    Connections {
        target: thumbs
        function onReady(key) { root.thumbKey = key }
    }
    Connections {
        target: player
        function onPathChanged() { root.thumbKey = ""; root.lastThumbT = -1 }
    }

    Item {
        id: track
        anchors.verticalCenter: parent.verticalCenter
        width: parent.width
        height: root.active ? 8 : 4
        Behavior on height { NumberAnimation { duration: 200; easing.type: Easing.OutCubic } }

        // base
        Rectangle { anchors.fill: parent; radius: height / 2; color: Qt.rgba(1, 1, 1, 0.16) }
        // buffered
        Rectangle {
            x: root.xAt(root.position)
            width: Math.max(0, Math.min(root.width - x, root.xAt(player.cacheTime) - root.xAt(0)))
            height: parent.height; radius: height / 2
            color: Qt.rgba(1, 1, 1, 0.22)
        }
        // hover ghost
        Rectangle {
            visible: root.active && !drag.pressed
            width: root.hoverX
            height: parent.height; radius: height / 2
            color: Qt.rgba(1, 1, 1, 0.16)
        }
        // progress
        Rectangle {
            id: prog
            width: Math.max(height, root.xAt(root.shownPos))
            height: parent.height; radius: height / 2
            gradient: Gradient {
                orientation: Gradient.Horizontal
                GradientStop { position: 0; color: Theme.accentA }
                GradientStop { position: 1; color: Theme.accentB }
            }
        }
        // A-B loop region
        Rectangle {
            readonly property bool hasA: player.abLoopA !== "no" && player.abLoopA !== undefined
            readonly property bool hasB: player.abLoopB !== "no" && player.abLoopB !== undefined
            visible: hasA
            x: root.xAt(Number(player.abLoopA))
            width: hasB ? Math.max(3, root.xAt(Number(player.abLoopB)) - x) : 3
            y: -3; height: parent.height + 6; radius: 3
            color: Qt.rgba(Theme.warn.r, Theme.warn.g, Theme.warn.b, 0.35)
            border.color: Theme.warn
        }
        // chapter gaps
        Repeater {
            model: player.chapters
            delegate: Rectangle {
                required property var modelData
                visible: modelData.time > 0.5
                x: root.xAt(modelData.time) - 1
                width: 2; height: track.height
                color: "#0b0c10"
                opacity: 0.85
            }
        }
    }

    // knob
    Rectangle {
        width: 16; height: 16; radius: 8
        x: root.xAt(root.shownPos) - 8
        anchors.verticalCenter: parent.verticalCenter
        color: "white"
        scale: drag.pressed ? 1.2 : hover.hovered ? 1 : 0
        Behavior on scale { NumberAnimation { duration: 260; easing.type: Easing.OutBack; easing.overshoot: 2.2 } }
        layer.enabled: true
        layer.effect: MultiEffect { shadowEnabled: true; shadowColor: Theme.accentA; shadowBlur: 0.8; shadowOpacity: 0.9 }
    }

    // preview bubble
    Item {
        id: bubble
        readonly property bool showThumb: prefs.thumbnails && root.thumbKey !== "" && !!(player.videoParams && player.videoParams.w)
        width: Math.max(timeCol.implicitWidth + 20, showThumb ? 176 : 0)
        height: (showThumb ? 99 + 8 : 0) + timeCol.implicitHeight + 14
        x: Math.max(-4, Math.min(root.width - width + 4, (drag.pressed ? root.xAt(root.dragPos) : root.hoverX) - width / 2))
        y: -height - 14
        opacity: root.active && root.duration > 0 ? 1 : 0
        scale: root.active ? 1 : 0.9
        transformOrigin: Item.Bottom
        visible: opacity > 0.01
        Behavior on opacity { NumberAnimation { duration: 160 } }
        Behavior on scale { NumberAnimation { duration: 220; easing.type: Easing.OutBack } }
        Behavior on height { NumberAnimation { duration: 160; easing.type: Easing.OutCubic } }

        Rectangle {
            anchors.fill: parent
            radius: 14
            color: "#161922"
            border.color: Theme.glassBorder
        }
        Item {
            id: thumbBox
            visible: bubble.showThumb
            x: 4; y: 4
            width: parent.width - 8; height: 99
            Image {
                id: thumbImg
                anchors.fill: parent
                source: root.thumbKey !== "" ? "image://thumb/" + root.thumbKey : ""
                fillMode: Image.PreserveAspectCrop
                asynchronous: false
                cache: false
                smooth: true
                visible: false
            }
            MultiEffect {
                anchors.fill: thumbImg
                source: thumbImg
                maskEnabled: true
                maskSource: thumbMask
            }
            Item {
                id: thumbMask
                anchors.fill: parent
                layer.enabled: true
                visible: false
                Rectangle { anchors.fill: parent; radius: 10 }
            }
        }
        Column {
            id: timeCol
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.bottom: parent.bottom
            anchors.bottomMargin: 7
            spacing: 1
            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                text: Theme.fmtTime(root.timeAt(drag.pressed ? root.xAt(root.dragPos) : root.hoverX))
                color: Theme.text
                font.family: Theme.font; font.pixelSize: 13; font.weight: Font.DemiBold
                font.features: { "tnum": 1 }
            }
            Text {
                readonly property string ch: root.chapterAt(root.timeAt(drag.pressed ? root.xAt(root.dragPos) : root.hoverX))
                visible: ch !== ""
                anchors.horizontalCenter: parent.horizontalCenter
                text: ch
                width: Math.min(implicitWidth, 220)
                elide: Text.ElideRight
                color: Theme.textDim
                font.family: Theme.font; font.pixelSize: 11
            }
        }
        // little pointer
        Rectangle {
            width: 10; height: 10; rotation: 45
            color: "#161922"
            border.color: Theme.glassBorder
            x: Math.max(10, Math.min(parent.width - 20, (drag.pressed ? root.xAt(root.dragPos) : root.hoverX) - bubble.x - 5))
            y: parent.height - 6
            z: -1
        }
    }

    HoverHandler {
        id: hover
        cursorShape: Qt.PointingHandCursor
        onPointChanged: {
            root.hoverX = Math.max(0, Math.min(root.width, point.position.x))
            if (hovered) root.requestThumb(root.timeAt(root.hoverX))
        }
    }
    MouseArea {
        id: drag
        anchors.fill: parent
        anchors.topMargin: -4; anchors.bottomMargin: -4
        preventStealing: true
        property real lastSeek: 0
        onPressed: (m) => {
            root.dragPos = root.timeAt(m.x)
            player.command(["seek", root.dragPos, "absolute+keyframes"])
        }
        onPositionChanged: (m) => {
            root.hoverX = Math.max(0, Math.min(root.width, m.x))
            root.dragPos = root.timeAt(m.x)
            root.requestThumb(root.dragPos)
            var now = Date.now()
            if (now - lastSeek > 60) { lastSeek = now; player.command(["seek", root.dragPos, "absolute+keyframes"]) }
        }
        onReleased: player.seekTo(root.dragPos)
    }
}
