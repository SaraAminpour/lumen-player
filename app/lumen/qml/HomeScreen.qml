import QtQuick
import QtQuick.Effects
import "."
import "Icons.js" as Icons

// Idle screen: animated aurora, gradient logo, quick actions, "continue watching" cards.
Item {
    id: home
    property var recent: []
    property bool active: true
    signal openRecent(string path)
    signal removeRecent(string path)

    onActiveChanged: if (active) enter.restart()
    Component.onCompleted: enter.restart()

    Rectangle { anchors.fill: parent; color: Theme.bg }

    Item {
        id: aurora
        anchors.fill: parent
        clip: true
        Blob {
            id: b1
            size: Math.max(home.width, home.height) * 0.95
            color: Theme.accentA
            x: home.width * 0.05 - size / 2; y: home.height * 0.15 - size / 2
            SequentialAnimation on x { loops: Animation.Infinite; running: home.active
                NumberAnimation { to: home.width * 0.45 - b1.size / 2; duration: 13000; easing.type: Easing.InOutSine }
                NumberAnimation { to: home.width * 0.05 - b1.size / 2; duration: 13000; easing.type: Easing.InOutSine } }
            SequentialAnimation on y { loops: Animation.Infinite; running: home.active
                NumberAnimation { to: home.height * 0.55 - b1.size / 2; duration: 9000; easing.type: Easing.InOutSine }
                NumberAnimation { to: home.height * 0.15 - b1.size / 2; duration: 9000; easing.type: Easing.InOutSine } }
        }
        Blob {
            id: b2
            size: Math.max(home.width, home.height) * 0.85
            color: Theme.accentB
            x: home.width * 0.95 - size / 2; y: home.height * 0.85 - size / 2
            SequentialAnimation on x { loops: Animation.Infinite; running: home.active
                NumberAnimation { to: home.width * 0.6 - b2.size / 2; duration: 11000; easing.type: Easing.InOutSine }
                NumberAnimation { to: home.width * 0.95 - b2.size / 2; duration: 11000; easing.type: Easing.InOutSine } }
            SequentialAnimation on y { loops: Animation.Infinite; running: home.active
                NumberAnimation { to: home.height * 0.35 - b2.size / 2; duration: 14000; easing.type: Easing.InOutSine }
                NumberAnimation { to: home.height * 0.85 - b2.size / 2; duration: 14000; easing.type: Easing.InOutSine } }
        }
        Blob {
            id: b3
            size: Math.max(home.width, home.height) * 0.6
            color: "#ec4899"
            opacity: 0.55
            x: home.width * 0.7 - size / 2; y: home.height * 0.1 - size / 2
            SequentialAnimation on x { loops: Animation.Infinite; running: home.active
                NumberAnimation { to: home.width * 0.3 - b3.size / 2; duration: 17000; easing.type: Easing.InOutSine }
                NumberAnimation { to: home.width * 0.7 - b3.size / 2; duration: 17000; easing.type: Easing.InOutSine } }
        }
        // vignette
        Rectangle {
            anchors.fill: parent
            gradient: Gradient {
                GradientStop { position: 0; color: Qt.rgba(0.03, 0.03, 0.05, 0.10) }
                GradientStop { position: 0.6; color: Qt.rgba(0.03, 0.03, 0.05, 0.20) }
                GradientStop { position: 1; color: Qt.rgba(0.03, 0.03, 0.05, 0.70) }
            }
        }
    }

    Column {
        id: content
        anchors.centerIn: parent
        anchors.verticalCenterOffset: home.recent.length > 0 ? -10 : 10
        spacing: 0
        width: Math.min(parent.width - 60, 980)

        Item {
            width: parent.width; height: 120
            Image {
                id: logo
                anchors.horizontalCenter: parent.horizontalCenter
                width: 92; height: 92
                source: "../assets/lumen.png"
                sourceSize: Qt.size(184, 184)
                smooth: true; mipmap: true
                SequentialAnimation on y { loops: Animation.Infinite; running: home.active
                    NumberAnimation { from: 10; to: 0; duration: 2200; easing.type: Easing.InOutSine }
                    NumberAnimation { from: 0; to: 10; duration: 2200; easing.type: Easing.InOutSine } }
                layer.enabled: true
                layer.effect: MultiEffect {
                    shadowEnabled: true
                    shadowColor: Theme.accentA
                    shadowBlur: 1
                    shadowOpacity: 0.7
                    shadowScale: 1.05
                }
            }
        }
        Item {
            width: parent.width; height: 64
            Rectangle {
                id: titleGrad
                anchors.centerIn: parent
                width: titleText.implicitWidth + 8; height: titleText.implicitHeight
                visible: false
                layer.enabled: true
                gradient: Gradient {
                    orientation: Gradient.Horizontal
                    GradientStop { position: 0; color: "#ffffff" }
                    GradientStop { position: 0.55; color: Qt.lighter(Theme.accentB, 1.3) }
                    GradientStop { position: 1; color: Qt.lighter(Theme.accentA, 1.4) }
                }
            }
            Text {
                id: titleText
                anchors.centerIn: parent
                text: "Lumen"
                visible: false
                layer.enabled: true
                font.family: Theme.font; font.pixelSize: 52; font.weight: Font.Bold; font.letterSpacing: -1.5
                color: "white"
            }
            MultiEffect {
                anchors.fill: titleGrad
                source: titleGrad
                maskEnabled: true
                maskSource: titleText
                maskThresholdMin: 0.5
                maskSpreadAtMin: 1.0
            }
        }
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: "Drop a video anywhere on this window, or pick one below."
            color: Theme.textDim
            font.family: Theme.font; font.pixelSize: 15
            bottomPadding: 26
        }
        Row {
            anchors.horizontalCenter: parent.horizontalCenter
            spacing: 12
            PillButton { primary: true; icon: Icons.folderOpenOutline; text: "Open file"; h: 46; fontSize: 14; hpad: 22; tip: "Ctrl+O"; onClicked: { openDialog.appendMode = false; openDialog.open() } }
            PillButton { icon: Icons.folderOutline; text: "Open folder"; h: 46; fontSize: 14; hpad: 20; onClicked: { folderDialog.appendMode = false; folderDialog.open() } }
            PillButton { icon: Icons.linkVariant; text: "Open URL"; h: 46; fontSize: 14; hpad: 20; tip: "Ctrl+U"; onClicked: urlDialog.open = true }
        }

        Item { width: 1; height: home.recent.length > 0 ? 44 : 0 }
        Text {
            visible: home.recent.length > 0
            text: "CONTINUE WATCHING"
            color: Theme.textFaint
            font.family: Theme.font; font.pixelSize: 11; font.weight: Font.Bold; font.letterSpacing: 1.4
            anchors.horizontalCenter: parent.horizontalCenter
            bottomPadding: 12
        }
        Flow {
            id: cards
            visible: home.recent.length > 0
            anchors.horizontalCenter: parent.horizontalCenter
            width: Math.min(parent.width, Math.max(1, Math.min(home.recent.length, Math.floor((parent.width + 12) / 232))) * 232 - 12)
            spacing: 12
            Repeater {
                model: home.recent.slice(0, 8)
                delegate: Item {
                    id: card
                    required property var modelData
                    required property int index
                    width: 220; height: 92
                    property real lift: cma.containsMouse ? -4 : 0
                    Behavior on lift { NumberAnimation { duration: 240; easing.type: Easing.OutCubic } }
                    opacity: 0
                    property real enterY: 18
                    transform: Translate { y: card.lift + card.enterY }
                    Connections {
                        target: enter
                        function onStarted() { card.opacity = 0; card.enterY = 18; cardIn.restart() }
                    }
                    SequentialAnimation {
                        id: cardIn
                        PauseAnimation { duration: 260 + card.index * 70 }
                        ParallelAnimation {
                            NumberAnimation { target: card; property: "opacity"; to: 1; duration: 380; easing.type: Easing.OutCubic }
                            NumberAnimation { target: card; property: "enterY"; to: 0; duration: 520; easing.type: Easing.OutBack }
                        }
                    }
                    Component.onCompleted: cardIn.restart()

                    Rectangle {
                        anchors.fill: parent
                        radius: 16
                        color: cma.containsMouse ? Qt.rgba(1, 1, 1, 0.10) : Qt.rgba(1, 1, 1, 0.055)
                        border.color: cma.containsMouse ? Qt.rgba(Theme.accentB.r, Theme.accentB.g, Theme.accentB.b, 0.55) : Theme.glassBorder
                        Behavior on color { ColorAnimation { duration: Theme.fast } }
                        Behavior on border.color { ColorAnimation { duration: Theme.fast } }
                    }
                    Rectangle {
                        x: 12; y: 12; width: 34; height: 34; radius: 10
                        gradient: Gradient {
                            orientation: Gradient.Horizontal
                            GradientStop { position: 0; color: Theme.accentA }
                            GradientStop { position: 1; color: Theme.accentB }
                        }
                        Icon { anchors.centerIn: parent; path: Icons.play; size: 20; color: "white"; scale: cma.containsMouse ? 1.15 : 1; Behavior on scale { NumberAnimation { duration: 240; easing.type: Easing.OutBack } } }
                    }
                    Column {
                        x: 56; y: 11
                        width: parent.width - 56 - 12
                        spacing: 2
                        Text {
                            width: parent.width
                            text: card.modelData.title || bridge.baseName(card.modelData.path)
                            elide: Text.ElideMiddle
                            color: Theme.text; font.family: Theme.font; font.pixelSize: 13; font.weight: Font.DemiBold
                        }
                        Text {
                            width: parent.width
                            text: card.modelData.dur > 0 ? Theme.fmtTime(Math.max(0, card.modelData.dur - card.modelData.pos)) + " left" : bridge.dirName(card.modelData.path)
                            elide: Text.ElideLeft
                            color: Theme.textFaint; font.family: Theme.font; font.pixelSize: 11
                        }
                    }
                    Rectangle {
                        x: 12; y: parent.height - 18
                        width: parent.width - 24; height: 4; radius: 2
                        color: Qt.rgba(1, 1, 1, 0.12)
                        Rectangle {
                            width: card.modelData.dur > 0 ? Math.max(4, parent.width * Math.min(1, card.modelData.pos / card.modelData.dur)) : 0
                            height: parent.height; radius: 2
                            gradient: Gradient {
                                orientation: Gradient.Horizontal
                                GradientStop { position: 0; color: Theme.accentA }
                                GradientStop { position: 1; color: Theme.accentB }
                            }
                        }
                    }
                    MouseArea {
                        id: cma
                        anchors.fill: parent
                        hoverEnabled: true
                        cursorShape: Qt.PointingHandCursor
                        onClicked: home.openRecent(card.modelData.path)
                    }
                    IconButton {
                        anchors.right: parent.right; anchors.top: parent.top
                        anchors.margins: 4
                        size: 26; iconSize: 14
                        icon: Icons.close
                        tip: "Remove from list"
                        opacity: cma.containsMouse || hovered ? 1 : 0
                        Behavior on opacity { NumberAnimation { duration: Theme.fast } }
                        onClicked: home.removeRecent(card.modelData.path)
                    }
                }
            }
        }
    }

    Text {
        anchors.bottom: parent.bottom
        anchors.bottomMargin: 18
        anchors.horizontalCenter: parent.horizontalCenter
        text: "Press  ?  for keyboard shortcuts   ·   right-click for the menu"
        color: Theme.textFaint
        font.family: Theme.font; font.pixelSize: 12
    }

    ParallelAnimation {
        id: enter
        NumberAnimation { target: content; property: "opacity"; from: 0; to: 1; duration: 600; easing.type: Easing.OutCubic }
        NumberAnimation { target: content; property: "scale"; from: 0.96; to: 1; duration: 700; easing.type: Easing.OutCubic }
    }
}
