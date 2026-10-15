import QtQuick
import QtQuick.Controls.Basic
import "."

LMenu {
    id: cmenu
    function popupAt(x, y) { cmenu.popup(x, y) }
    function tracksOf(type) {
        var out = [], ts = player.tracks || []
        for (var i = 0; i < ts.length; i++) if (ts[i].type === type) out.push(ts[i])
        return out
    }
    function trackName(t) {
        return (t.title || ((t.type === "sub" ? "Subtitle " : "Track ") + t.id)) + (t.lang ? "  (" + t.lang + ")" : "")
    }

    LMenuItem { text: "Open file…"; onTriggered: { openDialog.appendMode = false; openDialog.open() } }
    LMenuItem { text: "Open folder…"; onTriggered: { folderDialog.appendMode = false; folderDialog.open() } }
    LMenuItem { text: "Open URL…"; onTriggered: urlDialog.open = true }
    MenuSeparator { contentItem: Rectangle { implicitHeight: 1; color: Theme.glassBorder } }
    LMenu {
        id: subMenu
        title: "Subtitles"
        LMenuItem { text: "Off"; checkable: true; checked: player.sid === false || player.sid === "no"; onTriggered: player.setProp("sid", "no") }
        Instantiator {
            model: cmenu.tracksOf("sub")
            delegate: LMenuItem {
                required property var modelData
                text: cmenu.trackName(modelData)
                checkable: true
                checked: Number(player.sid) === modelData.id
                onTriggered: player.setProp("sid", modelData.id)
            }
            onObjectAdded: (i, o) => subMenu.insertItem(i + 1, o)
            onObjectRemoved: (i, o) => subMenu.removeItem(o)
        }
        MenuSeparator { contentItem: Rectangle { implicitHeight: 1; color: Theme.glassBorder } }
        LMenuItem { text: "Load subtitle file…"; onTriggered: subDialog.open() }
        LMenuItem { text: "Subtitle sync…"; onTriggered: win.showPanel("subs") }
    }
    LMenu {
        id: audioMenu
        title: "Audio"
        Instantiator {
            model: cmenu.tracksOf("audio")
            delegate: LMenuItem {
                required property var modelData
                text: cmenu.trackName(modelData)
                checkable: true
                checked: Number(player.aid) === modelData.id
                onTriggered: player.setProp("aid", modelData.id)
            }
            onObjectAdded: (i, o) => audioMenu.insertItem(i, o)
            onObjectRemoved: (i, o) => audioMenu.removeItem(o)
        }
        MenuSeparator { contentItem: Rectangle { implicitHeight: 1; color: Theme.glassBorder } }
        LMenuItem { text: "Load audio file…"; onTriggered: audioDialog.open() }
        LMenuItem { text: "Audio sync…"; onTriggered: win.showPanel("audio") }
    }
    LMenu {
        id: speedMenu
        title: "Speed"
        Instantiator {
            model: [0.5, 0.75, 1, 1.25, 1.5, 2]
            delegate: LMenuItem {
                required property var modelData
                text: modelData + "×"
                checkable: true
                checked: Math.abs(player.speed - modelData) < 0.001
                onTriggered: player.setProp("speed", modelData)
            }
            onObjectAdded: (i, o) => speedMenu.insertItem(i, o)
            onObjectRemoved: (i, o) => speedMenu.removeItem(o)
        }
    }
    LMenuItem { text: "Video adjustments…"; onTriggered: win.showPanel("video") }
    MenuSeparator { contentItem: Rectangle { implicitHeight: 1; color: Theme.glassBorder } }
    LMenuItem { text: "Take screenshot"; enabled: !player.idle; onTriggered: win.screenshot() }
    LMenuItem { text: "Always on top"; checkable: true; checked: win.pinned; onTriggered: win.togglePin() }
    LMenuItem { text: "Full screen"; checkable: true; checked: win.fullscreen; onTriggered: win.toggleFullscreen() }
    LMenuItem { text: "Media info"; onTriggered: win.showPanel("info") }
    LMenuItem { text: "Keyboard shortcuts"; onTriggered: shortcutsSheet.open = true }
    MenuSeparator { contentItem: Rectangle { implicitHeight: 1; color: Theme.glassBorder } }
    LMenuItem { text: "Quit"; onTriggered: win.close() }
}
