import QtQuick
import QtQuick.Controls.Basic
import "."
import "Icons.js" as Icons

Modal {
    id: dlg
    cardWidth: 560
    cardHeight: 220
    onOpenChanged: if (open) {
        var c = bridge.clipboardText()
        field.text = /^(https?|rtmp|rtsp|udp|mms|ftp):\/\//i.test(c) ? c : ""
        field.forceActiveFocus()
        field.selectAll()
    }
    function go() {
        if (field.text.trim() === "") return
        win.openTargets([field.text.trim()], false)
        dlg.open = false
    }
    Column {
        anchors.fill: parent
        anchors.margins: 24
        spacing: 14
        Row {
            spacing: 10
            Icon { path: Icons.linkVariant; size: 22; color: Theme.accent; anchors.verticalCenter: parent.verticalCenter }
            Text { text: "Open a stream or URL"; color: Theme.text; font.family: Theme.font; font.pixelSize: 18; font.weight: Font.Bold }
        }
        TextField {
            id: field
            width: parent.width
            height: 44
            placeholderText: "https://…   (direct links, HLS/DASH, YouTube with yt-dlp installed)"
            placeholderTextColor: Theme.textFaint
            color: Theme.text
            font.family: Theme.font; font.pixelSize: 13
            leftPadding: 14; rightPadding: 14
            selectByMouse: true
            selectionColor: Theme.accentA
            background: Rectangle {
                radius: 12
                color: Qt.rgba(1, 1, 1, 0.06)
                border.color: field.activeFocus ? Theme.accent : Theme.glassBorder
                Behavior on border.color { ColorAnimation { duration: Theme.fast } }
            }
            Keys.onReturnPressed: dlg.go()
            Keys.onEnterPressed: dlg.go()
            Keys.onEscapePressed: dlg.open = false
        }
        Row {
            anchors.right: parent.right
            spacing: 8
            PillButton { text: "Cancel"; onClicked: dlg.open = false }
            PillButton { text: "Play"; icon: Icons.play; primary: true; onClicked: dlg.go() }
        }
    }
}
