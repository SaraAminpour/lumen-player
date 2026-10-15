import QtQuick
import QtQuick.Window
import QtQuick.Controls.Basic
import QtQuick.Dialogs
import QtCore
import Lumen
import "."
import "Icons.js" as Icons

ApplicationWindow {
    id: win
    width: 1280
    height: 760
    minimumWidth: 600
    minimumHeight: 360
    visible: true
    color: Theme.bg
    title: player.idle ? "Lumen" : (player.mediaTitle || player.fileName) + " — Lumen"
    flags: Qt.Window | Qt.FramelessWindowHint | (pinned ? Qt.WindowStaysOnTopHint : 0)

    property bool pinned: false
    property bool fakeFullscreen: false     // Windows: borderless window over the monitor (see Bridge.setFullscreen)
    readonly property bool fullscreen: visibility === Window.FullScreen || fakeFullscreen
    property int prevVisibility: Window.Windowed
    property string panel: ""
    property bool loading: false
    property var recent: []
    readonly property bool hasVideo: !!(player.videoParams && player.videoParams.w)
    readonly property bool uiVisible: player.idle || active
                                      || panel !== "" || ctxMenu.visible || player.paused || loading
                                      || urlDialog.open || shortcutsSheet.open || dock.speedOpen

    // ------------------------------------------------------------ prefs
    Settings {
        id: prefs
        property int accent: 0
        property bool glassBlur: true
        property bool thumbnails: true
        property bool showRemaining: false
        property bool resume: true
        property bool autoPlaylist: true
        property bool pauseOnMinimize: false
        property bool rememberVolume: true
        property real volume: 100
        property bool volumeBoost: false
        property bool pitchCorrection: true
        property bool downmix: false
        property bool hwdec: true
        property bool fillCrop: false
        property real subScale: 1
        property int subPos: 100
        property string subColor: "#FFFFFF"
        property bool subBox: false
        property bool subOverride: false
        property string recentJson: "[]"
        property int winW: 1280
        property int winH: 760
        property bool winMax: false
    }

    Component.onCompleted: {
        Theme.font = uiFont
        Theme.accentIndex = Math.max(0, Math.min(Theme.accents.length - 1, prefs.accent))
        try { recent = JSON.parse(prefs.recentJson) } catch (e) { recent = [] }
        width = Math.max(minimumWidth, prefs.winW)
        height = Math.max(minimumHeight, prefs.winH)
        x = (Screen.width - width) / 2
        y = (Screen.height - height) / 2
        if (prefs.winMax) showMaximized()
        if (prefs.rememberVolume) player.setProp("volume", Math.min(prefs.volume, prefs.volumeBoost ? 200 : 100))
        player.setProp("hwdec", prefs.hwdec ? "auto-safe" : "no")
        player.setProp("sub-scale", prefs.subScale)
        applySubPos()
        player.setProp("audio-pitch-correction", prefs.pitchCorrection)
        if (prefs.downmix) player.setProp("audio-channels", "stereo")
        if (prefs.fillCrop) player.setProp("panscan", 1.0)
        if (testShot === "") {
            player.setProp("save-position-on-quit", prefs.resume)
            player.setProp("resume-playback", prefs.resume)
        }
        applySubStyle()
        bridge.styleWindow(win, false)
        for (var i = 0; i < testActions.length; i++) runTest(testActions[i])
    }
    onClosing: {
        rememberPosition()
        if (!fullscreen) {
            prefs.winMax = visibility === Window.Maximized
            if (!prefs.winMax) { prefs.winW = width; prefs.winH = height }
        }
        prefs.volume = player.volume
        player.saveState()
    }
    onVisibilityChanged: {
        if (tracing) trace("visibility", win.visibility)
        if (win.visibility === Window.Minimized && prefs.pauseOnMinimize && !player.paused) player.setProp("pause", true)
    }

    // lift subtitles above the dock while controls are showing
    readonly property real subLift: uiVisible && !player.idle && hasVideo ? (dock.height + 28) / Math.max(1, stage.height) * 100 : 0
    onSubLiftChanged: applySubPos()
    function applySubPos() { player.setProp("sub-pos", Math.round(Math.max(30, Math.min(prefs.subPos, 100 - subLift)))) }

    // ------------------------------------------------------------ actions
    function poke() { active = true; activity.restart() }

    property var deferredOpen: null
    function openTargets(list, append) {
        if (!video.ready) { deferredOpen = { list: list, append: append }; loading = true; return }
        var media = [], subs = []
        for (var i = 0; i < list.length; i++) {
            var t = String(list[i])
            var k = bridge.kind(t)
            if (k === "sub") subs.push(t)
            else if (k === "dir") media = media.concat(bridge.folderMedia(t))
            else media.push(t)
        }
        if (media.length === 0) {
            if (subs.length && !player.idle) {
                for (var s = 0; s < subs.length; s++) player.addSubtitle(subs[s])
                toast.show("Subtitle loaded", Icons.subtitles)
            }
            return
        }
        if (append) {
            for (var a = 0; a < media.length; a++) player.open(media[a], "append")
            toast.show(media.length === 1 ? "Added to playlist" : media.length + " files added", Icons.playlistPlay)
            return
        }
        rememberPosition()
        loading = true
        pendingSubs = subs
        if (media.length === 1 && prefs.autoPlaylist && bridge.kind(media[0]) !== "other") {
            var sib = bridge.siblings(media[0])
            var me = bridge.baseName(media[0])
            var idx = -1
            for (var j = 0; j < sib.length; j++) if (bridge.baseName(sib[j]) === me) { idx = j; break }
            if (idx >= 0 && sib.length > 1) {
                player.open(sib[idx], "replace")
                for (var n = idx + 1; n < sib.length; n++) player.open(sib[n], "append")
                for (var p = 0; p < idx; p++) player.open(sib[p], "append")
                var after = sib.length - idx - 1
                for (var q = 0; q < idx; q++) player.command(["playlist-move", after + 1 + q, q])
                return
            }
        }
        player.open(media[0], "replace")
        for (var m = 1; m < media.length; m++) player.open(media[m], "append")
    }
    property var pendingSubs: []

    function rememberPosition() {
        if (player.idle || !player.path || player.duration <= 0) return
        var p = player.path, r = recent.filter(function (e) { return e.path !== p })
        var nearEnd = player.duration - player.position < 30
        r.unshift({ path: p, pos: nearEnd ? 0 : player.position, dur: player.duration, t: Date.now() })
        recent = r.slice(0, 12)
        prefs.recentJson = JSON.stringify(recent)
    }
    function removeRecent(path) {
        recent = recent.filter(function (e) { return e.path !== path })
        prefs.recentJson = JSON.stringify(recent)
    }

    function togglePause() {
        if (player.idle) return
        flash.flash(player.paused ? Icons.play : Icons.pause)
        player.cycle("pause")
    }
    function seekRel(s) {
        if (player.idle) return
        player.seekBy(s)
        if (s < 0) leftRipple.hit(s); else rightRipple.hit(s)
    }
    function nextItem() {
        if (player.playlistPos < player.playlist.length - 1) { rememberPosition(); player.command(["playlist-next"]) }
        else toast.show("End of playlist", Icons.playlistPlay)
    }
    function prevItem() {
        if (player.position > 3 || player.playlistPos <= 0) player.seekTo(0)
        else { rememberPosition(); player.command(["playlist-prev"]) }
    }
    function toggleFullscreen() {
        if (fakeFullscreen) {
            fakeFullscreen = false
            bridge.setFullscreen(win, false)
            if (prevVisibility === Window.Maximized) showMaximized()
            if (tracing) Qt.callLater(function () { bridge.log("TRACE after exit " + win.visibility + " " + win.x + "," + win.y + " " + win.width + "x" + win.height) })
        } else if (visibility === Window.FullScreen) {
            if (prevVisibility === Window.Maximized) showMaximized(); else showNormal()
        } else {
            prevVisibility = visibility
            if (isWindows) {
                if (visibility === Window.Maximized) showNormal()
                if (bridge.setFullscreen(win, true)) { fakeFullscreen = true; return }
            }
            showFullScreen()
        }
    }
    function toggleMaximize() {
        if (fullscreen) return
        if (visibility === Window.Maximized) showNormal(); else showMaximized()
    }
    function togglePin() {
        pinned = !pinned
        toast.show(pinned ? "Always on top" : "Normal window", pinned ? Icons.pin : Icons.pinOff)
    }
    function showPanel(name) { panel = name; sidePanel.page = name }
    function togglePanel(name) {
        if (panel === name || (name === "settings" && (panel === "settings" || panel === "info"))) panel = ""
        else showPanel(name)
    }
    function screenshot(mode) {
        if (player.idle) return
        player.command(["screenshot", mode || "subtitles"])
        shutter.restart()
        toast.show("Screenshot saved to Pictures › Lumen Screenshots", Icons.cameraOutline)
    }
    function abLoop() {
        if (player.idle) return
        var a = player.abLoopA, b = player.abLoopB
        player.command(["ab-loop"])
        if (a === "no") toast.show("Loop start set at " + Theme.fmtTime(player.position) + " — press B again for the end", Icons.abTesting)
        else if (b === "no") toast.show("Looping " + Theme.fmtTime(Number(a)) + " → " + Theme.fmtTime(player.position), Icons.repeat)
        else toast.show("A-B loop cleared", Icons.repeatOff)
    }
    function toggleLoopFile() {
        var on = player.loopFile !== false && player.loopFile !== "no"
        player.setProp("loop-file", on ? "no" : "inf")
        toast.show(on ? "Loop off" : "Looping this file", on ? Icons.repeatOff : Icons.repeatOnce)
    }
    function subStep(n) {
        if (player.idle) return
        player.command(["sub-step", n])
        subToast.restart()
    }
    function nudgeSub(d) {
        if (player.idle) return
        var v = Math.round((player.subDelay + d) * 1000) / 1000
        player.setProp("sub-delay", v)
        toast.show("Subtitle delay " + Theme.fmtDelay(v), Icons.timerOutline)
    }
    function nudgeAudio(d) {
        if (player.idle) return
        var v = Math.round((player.audioDelay + d) * 1000) / 1000
        player.setProp("audio-delay", v)
        toast.show("Audio delay " + Theme.fmtDelay(v), Icons.waveform)
    }
    function changeVolume(d) {
        var max = prefs.volumeBoost ? 200 : 100
        var v = Math.max(0, Math.min(max, Math.round(player.volume + d)))
        if (player.muted) player.setProp("mute", false)
        player.setProp("volume", v)
        toast.show(v + "%", v === 0 ? Icons.volumeOff : Icons.volumeHigh, v / max)
    }
    function changeSpeed(d) {
        var v = Math.max(0.25, Math.min(4, Math.round((player.speed + d) * 100) / 100))
        player.setProp("speed", v)
        toast.show("Speed " + v + "×", Icons.playSpeed)
    }
    function cycleTrack(type) {
        player.cycle(type === "sub" ? "sid" : "aid")
        trackToast.kind = type
        trackToast.restart()
    }
    function applySubStyle() {
        player.setProp("sub-color", prefs.subColor)
        player.setProp("sub-ass-override", prefs.subOverride ? "force" : "scale")
        if (prefs.subBox) {
            player.setProp("sub-border-style", "opaque-box")
            player.setProp("sub-back-color", "#B3000000")
        } else {
            player.setProp("sub-border-style", "outline-and-shadow")
            player.setProp("sub-back-color", "#00000000")
        }
    }
    function trackLabel(type, id) {
        if (id === false || id === "no") return "Off"
        var ts = player.tracks || []
        for (var i = 0; i < ts.length; i++)
            if (ts[i].type === type && ts[i].id === Number(id))
                return (ts[i].title || ((type === "sub" ? "Subtitle " : "Track ") + ts[i].id)) + (ts[i].lang ? " (" + ts[i].lang + ")" : "")
        return "#" + id
    }
    function runTest(a) {
        if (a === "subs" || a === "audio" || a === "video" || a === "playlist" || a === "info" || a === "settings") showPanel(a)
        else if (a === "keys") shortcutsSheet.open = true
        else if (a === "url") urlDialog.open = true
        else if (a === "menu") menuTimer.start()
        else if (a === "toast") toastTimer.start()
        else if (a === "speed") speedPopTimer.start()
        else if (a.indexOf("accent") === 0) Theme.accentIndex = Number(a.slice(6))
        else if (a === "recent") recent = [{ path: "/media/Videos/The Last Summit.mp4", pos: 5400, dur: 7920 }, { path: "/media/Videos/Neon Rain.mp4", pos: 1500, dur: 3300 }, { path: "/media/Videos/Lecture 07 - Quantum Circuits.mkv", pos: 400, dur: 4860 }]
        else if (a === "seekhover") seekHoverTimer.start()
        else if (a === "ripple") rightRipple.hit(10)
        else if (a === "fs") toggleFullscreen()
        else if (a === "fsback") fsBackTimer.start()
        else if (a === "fslater") fsLaterTimer.start()
        else if (a === "max") toggleMaximize()
    }
    // headless test capture: --test-shot=file.png@delay_ms
    Timer {
        running: testShot !== ""
        interval: testShot.indexOf("@") > 0 ? Number(testShot.split("@")[1]) : 4000
        onTriggered: stage.grabToImage(function (r) { r.saveToFile(testShot.split("@")[0]); console.log("saved"); Qt.quit() })
    }
    Timer { id: toastTimer; interval: 4300; onTriggered: { win.poke(); toast.show("Subtitle delay +0.250 s", Icons.timerOutline); rightRipple.hit(10) } }
    Timer { id: fsLaterTimer; interval: 2000; onTriggered: win.toggleFullscreen() }
    Timer { id: fsBackTimer; interval: 5000; onTriggered: win.toggleFullscreen() }
    Timer { id: menuTimer; interval: 2500; onTriggered: ctxMenu.popupAt(420, 160) }
    Timer { id: speedPopTimer; interval: 1500; onTriggered: { win.poke(); dock.openSpeed() } }
    Timer { id: seekHoverTimer; interval: 2500; onTriggered: dock.simulateHover() }

    // controls + cursor hide after 3 s without mouse movement
    property bool active: true
    Timer { id: activity; interval: 3000; running: true; onTriggered: win.active = false }
    // --act=trace: log UI state changes (used by the automated tests)
    readonly property bool tracing: testActions.indexOf("trace") >= 0
    function trace() { bridge.log("TRACE " + Array.prototype.slice.call(arguments).join(" ") + " " + Date.now() % 100000) }
    onUiVisibleChanged: if (tracing) trace("ui", uiVisible)
    Connections { target: player; enabled: win.tracing; function onPausedChanged() { trace("paused", player.paused) } }
    Timer { id: subToast; interval: 150; onTriggered: toast.show("Subtitle delay " + Theme.fmtDelay(player.subDelay), Icons.sync) }
    Timer {
        id: trackToast
        property string kind: "sub"
        interval: 150
        onTriggered: kind === "sub" ? toast.show("Subtitles: " + trackLabel("sub", player.sid), Icons.subtitles)
                                    : toast.show("Audio: " + trackLabel("audio", player.aid), Icons.musicNote)
    }
    Timer { interval: 5000; repeat: true; running: !player.idle && !player.paused; onTriggered: rememberPosition() }

    Connections {
        target: bridge
        function onOpenRequested(files) {
            if (files.length > 0) win.openTargets(files, false)
            if (win.visibility === Window.Minimized) win.showNormal()
            win.raise()
            win.requestActivate()
        }
    }
    Connections {
        target: player
        function onFileLoaded() {
            win.loading = false
            for (var i = 0; i < win.pendingSubs.length; i++) player.addSubtitle(win.pendingSubs[i])
            win.pendingSubs = []
            win.rememberPosition()
            if (player.position > 5) toast.show("Resumed at " + Theme.fmtTime(player.position), Icons.history)
            win.poke()
        }
        function onEndFile(reason) {
            win.loading = false
            if (reason.indexOf("ERROR") >= 0 || reason === "4") toast.show("Couldn't play this file", Icons.close)
        }
    }

    // ------------------------------------------------------------ dialogs
    FileDialog {
        id: openDialog
        property bool appendMode: false
        title: appendMode ? "Add to playlist" : "Open media"
        fileMode: FileDialog.OpenFiles
        nameFilters: ["Media files (*.mkv *.mp4 *.m4v *.avi *.mov *.webm *.wmv *.flv *.ts *.m2ts *.mpg *.mpeg *.ogv *.3gp *.vob *.mp3 *.flac *.m4a *.aac *.ogg *.opus *.wav *.mka)", "All files (*)"]
        onAccepted: {
            var l = []
            for (var i = 0; i < selectedFiles.length; i++) l.push(selectedFiles[i].toString())
            win.openTargets(l, appendMode)
        }
    }
    FolderDialog {
        id: folderDialog
        property bool appendMode: false
        title: "Open a folder"
        onAccepted: win.openTargets([selectedFolder.toString()], appendMode)
    }
    FileDialog {
        id: subDialog
        title: "Load subtitles"
        nameFilters: ["Subtitles (*.srt *.ass *.ssa *.vtt *.sub *.sup *.idx *.smi)", "All files (*)"]
        onAccepted: { player.addSubtitle(selectedFile.toString()); toast.show("Subtitle loaded", Icons.subtitles) }
    }
    FileDialog {
        id: audioDialog
        title: "Load an audio track"
        nameFilters: ["Audio (*.mp3 *.flac *.m4a *.aac *.ogg *.opus *.wav *.mka *.ac3 *.dts *.eac3)", "All files (*)"]
        onAccepted: { player.addAudio(selectedFile.toString()); toast.show("Audio track loaded", Icons.musicNote) }
    }

    // ------------------------------------------------------------ stage
    Item {
        id: stage
        anchors.fill: parent
        focus: true
        Keys.onPressed: (e) => { if (e.text === "?") { shortcutsSheet.open = !shortcutsSheet.open; e.accepted = true } }

        HoverHandler {
            cursorShape: win.uiVisible || player.idle ? Qt.ArrowCursor : Qt.BlankCursor
            // Qt re-sends hover events on every rendered frame, so only count real movement
            property point last: Qt.point(-1, -1)
            onPointChanged: {
                var p = point.scenePosition
                if (Math.abs(p.x - last.x) + Math.abs(p.y - last.y) > 2) { if (win.tracing) win.trace("hover", last, p); last = p; win.poke() }
            }
        }

        VideoItem {
            id: video
            anchors.fill: parent
            onReadyChanged: {
                if (startupFiles.length > 0) win.openTargets(startupFiles, false)
                else if (win.deferredOpen) win.openTargets(win.deferredOpen.list, win.deferredOpen.append)
                win.deferredOpen = null
            }
        }

        // audio-only visual
        Item {
            anchors.fill: parent
            visible: !player.idle && !win.hasVideo && !win.loading
            Rectangle { anchors.fill: parent; color: Theme.bg }
            Blob { size: Math.min(parent.width, parent.height) * 1.1; anchors.centerIn: parent; color: Theme.accentA; opacity: 0.6
                SequentialAnimation on scale { loops: Animation.Infinite; running: !player.paused
                    NumberAnimation { to: 1.12; duration: 1600; easing.type: Easing.InOutSine }
                    NumberAnimation { to: 0.92; duration: 1600; easing.type: Easing.InOutSine } } }
            Column {
                anchors.centerIn: parent
                spacing: 16
                Rectangle {
                    anchors.horizontalCenter: parent.horizontalCenter
                    width: 150; height: 150; radius: 75
                    gradient: Gradient {
                        orientation: Gradient.Horizontal
                        GradientStop { position: 0; color: Theme.accentA }
                        GradientStop { position: 1; color: Theme.accentB }
                    }
                    Icon { anchors.centerIn: parent; path: Icons.musicNote; size: 72; color: "white" }
                    RotationAnimator on rotation { from: 0; to: 360; duration: 14000; loops: Animation.Infinite; running: !player.paused }
                }
                Text { anchors.horizontalCenter: parent.horizontalCenter; text: player.mediaTitle; color: Theme.text; font.family: Theme.font; font.pixelSize: 22; font.weight: Font.Bold }
            }
        }

        // pointer interaction on the video surface
        Item {
            id: surface
            anchors.fill: parent
            // A single click pauses after a short wait; a double click (judged with the system
            // double-click speed) goes fullscreen on the second press and never leaves it paused.
            MouseArea {
                anchors.fill: parent
                acceptedButtons: Qt.LeftButton | Qt.RightButton
                property point pressAt
                property real lastPress: 0
                property bool dragged: false
                property bool second: false
                property bool pausedByFirst: false
                function isSecond(x, y) {
                    return Date.now() - lastPress <= Math.max(300, Qt.styleHints.mouseDoubleClickInterval)
                           && Math.abs(x - pressAt.x) + Math.abs(y - pressAt.y) < 24
                }
                function goFullscreen() {
                    if (clickWait.running) clickWait.stop()
                    else if (pausedByFirst) win.togglePause()      // slow double click: undo the pause
                    pausedByFirst = false
                    lastPress = 0
                    win.toggleFullscreen()
                }
                onPressed: (m) => {
                    win.poke()
                    if (m.button === Qt.RightButton) { ctxMenu.popupAt(m.x, m.y); return }
                    dragged = false
                    second = lastPress > 0 && isSecond(m.x, m.y)
                    if (second) { goFullscreen(); return }
                    pressAt = Qt.point(m.x, m.y)
                    lastPress = Date.now()
                    pausedByFirst = false
                }
                // some platforms report the second press only as a double-click event
                onDoubleClicked: (m) => { if (!second && m.button === Qt.LeftButton) { second = true; goFullscreen() } }
                onPositionChanged: (m) => {
                    if (!pressed || dragged || second || !(m.buttons & Qt.LeftButton)) return
                    if (Math.abs(m.x - pressAt.x) + Math.abs(m.y - pressAt.y) > 6) {
                        dragged = true
                        lastPress = 0
                        if (!win.fullscreen && win.visibility !== Window.Maximized) win.startSystemMove()
                    }
                }
                onReleased: (m) => {
                    if (m.button !== Qt.LeftButton || dragged || second) return
                    clickWait.restart()
                }
                Timer {
                    id: clickWait
                    interval: 220
                    onTriggered: { parent.pausedByFirst = true; win.togglePause() }
                }
            }
            WheelHandler {
                onWheel: (ev) => { win.poke(); win.changeVolume(ev.angleDelta.y > 0 ? 5 : -5) }
            }
        }

        HomeScreen {
            id: home
            anchors.fill: parent
            recent: win.recent
            active: player.idle && !win.loading
            opacity: active ? 1 : 0
            scale: active ? 1 : 1.04
            visible: opacity > 0.01
            Behavior on opacity { NumberAnimation { duration: 450; easing.type: Easing.OutCubic } }
            Behavior on scale { NumberAnimation { duration: 600; easing.type: Easing.OutCubic } }
            onOpenRecent: (p) => win.openTargets([p], false)
            onRemoveRecent: (p) => win.removeRecent(p)
        }

        Spinner {
            anchors.centerIn: parent
            visible: win.loading || player.buffering
        }

        SeekRipple { id: leftRipple; forward: false; anchors.left: parent.left }
        SeekRipple { id: rightRipple; forward: true; anchors.right: parent.right }
        CenterFlash { id: flash; anchors.centerIn: parent }

        // scrims for legibility
        Rectangle {
            anchors.top: parent.top
            width: parent.width; height: 150
            opacity: win.uiVisible && !player.idle ? 1 : 0
            Behavior on opacity { NumberAnimation { duration: Theme.med } }
            gradient: Gradient {
                GradientStop { position: 0; color: Qt.rgba(0, 0, 0, 0.6) }
                GradientStop { position: 1; color: "transparent" }
            }
        }
        Rectangle {
            anchors.bottom: parent.bottom
            width: parent.width; height: 220
            opacity: win.uiVisible && !player.idle ? 1 : 0
            Behavior on opacity { NumberAnimation { duration: Theme.med } }
            gradient: Gradient {
                GradientStop { position: 0; color: "transparent" }
                GradientStop { position: 1; color: Qt.rgba(0, 0, 0, 0.55) }
            }
        }

        TopBar {
            id: topBar
            anchors.top: parent.top
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.rightMargin: win.panel !== "" ? sidePanel.width + 18 : 0
            Behavior on anchors.rightMargin { NumberAnimation { duration: 420; easing.type: Easing.OutCubic } }
            property real shift: win.uiVisible ? 0 : -24
            Behavior on shift { NumberAnimation { duration: 320; easing.type: Easing.OutCubic } }
            transform: Translate { y: topBar.shift }
            opacity: win.uiVisible ? 1 : 0
            Behavior on opacity { NumberAnimation { duration: 280 } }
            visible: opacity > 0.01
            enabled: win.uiVisible       // a hidden bar must not catch clicks
        }

        ControlDock {
            id: dock
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.bottom: parent.bottom
            anchors.leftMargin: 16
            anchors.bottomMargin: 16
            anchors.rightMargin: win.panel !== "" ? sidePanel.width + 30 : 16
            Behavior on anchors.rightMargin { NumberAnimation { duration: 420; easing.type: Easing.OutCubic } }
            blurSource: video
            shift: win.uiVisible && !player.idle ? 0 : 40
            Behavior on shift { NumberAnimation { duration: 340; easing.type: Easing.OutCubic } }
            transform: Translate { y: dock.shift }
            enabled: win.uiVisible && !player.idle
            opacity: win.uiVisible && !player.idle ? 1 : 0
            Behavior on opacity { NumberAnimation { duration: 280 } }
            visible: opacity > 0.01
        }

        SidePanel {
            id: sidePanel
            width: Math.min(410, stage.width * 0.55)
            anchors.top: parent.top
            anchors.bottom: parent.bottom
            anchors.right: parent.right
            anchors.margins: 12
            blurSource: player.idle ? null : video
            shift: win.panel !== "" ? 0 : width + 40
            Behavior on shift { NumberAnimation { duration: 460; easing.type: Easing.OutQuint } }
            transform: Translate { x: sidePanel.shift }
            opacity: win.panel !== "" ? 1 : 0
            Behavior on opacity { NumberAnimation { duration: 260 } }
            visible: opacity > 0.01
        }

        Toast {
            id: toast
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.horizontalCenterOffset: win.panel !== "" ? -(sidePanel.width + 12) / 2 : 0
            y: 72
            z: 40
        }

        // camera shutter flash
        Rectangle {
            id: shutterRect
            anchors.fill: parent
            color: "white"
            opacity: 0
            z: 45
            NumberAnimation { id: shutter; target: shutterRect; property: "opacity"; from: 0.55; to: 0; duration: 420; easing.type: Easing.OutCubic }
        }

        // drop target
        DropArea {
            id: drop
            anchors.fill: parent
            property bool subsOnly: false
            onEntered: (d) => {
                var only = d.urls.length > 0
                for (var i = 0; i < d.urls.length; i++) if (bridge.kind(d.urls[i].toString()) !== "sub") only = false
                subsOnly = only
            }
            onDropped: (d) => {
                var l = []
                for (var i = 0; i < d.urls.length; i++) l.push(d.urls[i].toString())
                win.openTargets(l, false)
            }
        }
        Item {
            anchors.fill: parent
            z: 60
            opacity: drop.containsDrag ? 1 : 0
            visible: opacity > 0.01
            Behavior on opacity { NumberAnimation { duration: 200 } }
            Rectangle { anchors.fill: parent; color: Qt.rgba(0.03, 0.03, 0.06, 0.72) }
            Rectangle {
                anchors.fill: parent
                anchors.margins: 22
                radius: 28
                color: Qt.rgba(Theme.accentA.r, Theme.accentA.g, Theme.accentA.b, 0.08)
                border.width: 2
                border.color: Theme.accentB
                SequentialAnimation on border.color {
                    loops: Animation.Infinite
                    running: drop.containsDrag
                    ColorAnimation { to: Theme.accentA; duration: 700 }
                    ColorAnimation { to: Theme.accentB; duration: 700 }
                }
            }
            Column {
                anchors.centerIn: parent
                spacing: 14
                Icon {
                    anchors.horizontalCenter: parent.horizontalCenter
                    path: drop.subsOnly ? Icons.subtitles : Icons.movieOpenPlayOutline
                    size: 72; color: "white"
                    SequentialAnimation on y {
                        loops: Animation.Infinite; running: drop.containsDrag
                        NumberAnimation { from: 0; to: -10; duration: 600; easing.type: Easing.InOutSine }
                        NumberAnimation { from: -10; to: 0; duration: 600; easing.type: Easing.InOutSine }
                    }
                }
                Text {
                    anchors.horizontalCenter: parent.horizontalCenter
                    text: drop.subsOnly ? "Drop to add subtitles" : "Drop to play"
                    color: "white"; font.family: Theme.font; font.pixelSize: 24; font.weight: Font.Bold
                }
            }
        }

        UrlDialog { id: urlDialog }
        ShortcutsSheet { id: shortcutsSheet }
        ContextMenu { id: ctxMenu }

        // resize grips (frameless window)
        Repeater {
            model: [
                { e: Qt.LeftEdge, c: Qt.SizeHorCursor }, { e: Qt.RightEdge, c: Qt.SizeHorCursor },
                { e: Qt.TopEdge, c: Qt.SizeVerCursor }, { e: Qt.BottomEdge, c: Qt.SizeVerCursor },
                { e: Qt.LeftEdge | Qt.TopEdge, c: Qt.SizeFDiagCursor }, { e: Qt.RightEdge | Qt.BottomEdge, c: Qt.SizeFDiagCursor },
                { e: Qt.RightEdge | Qt.TopEdge, c: Qt.SizeBDiagCursor }, { e: Qt.LeftEdge | Qt.BottomEdge, c: Qt.SizeBDiagCursor }
            ]
            delegate: Item {
                required property var modelData
                readonly property int e: modelData.e
                readonly property int g: 6
                visible: !win.fullscreen && win.visibility !== Window.Maximized
                z: 100
                x: (e & Qt.RightEdge) ? stage.width - g : 0
                y: (e & Qt.BottomEdge) ? stage.height - g : 0
                width: (e & (Qt.LeftEdge | Qt.RightEdge)) && !(e & (Qt.TopEdge | Qt.BottomEdge)) ? g
                       : (e & (Qt.TopEdge | Qt.BottomEdge)) && !(e & (Qt.LeftEdge | Qt.RightEdge)) ? stage.width : g * 2
                height: (e & (Qt.TopEdge | Qt.BottomEdge)) && !(e & (Qt.LeftEdge | Qt.RightEdge)) ? g
                        : (e & (Qt.LeftEdge | Qt.RightEdge)) && !(e & (Qt.TopEdge | Qt.BottomEdge)) ? stage.height : g * 2
                HoverHandler { cursorShape: modelData.c }
                DragHandler { target: null; onActiveChanged: if (active) win.startSystemResize(parent.e) }
            }
        }
    }

    // ------------------------------------------------------------ keys
    component Key: Shortcut { context: Qt.ApplicationShortcut; autoRepeat: true }

    Key { sequences: ["Space", "K", "Media Play"]; autoRepeat: false; onActivated: { win.poke(); win.togglePause() } }
    Key { sequences: ["Left"]; onActivated: win.seekRel(-5) }
    Key { sequences: ["Right"]; onActivated: win.seekRel(5) }
    Key { sequences: ["Shift+Left"]; onActivated: win.seekRel(-1) }
    Key { sequences: ["Shift+Right"]; onActivated: win.seekRel(1) }
    Key { sequences: ["J"]; onActivated: win.seekRel(-10) }
    Key { sequences: ["L"]; onActivated: win.seekRel(10) }
    Key { sequences: ["Ctrl+Left"]; onActivated: win.seekRel(-60) }
    Key { sequences: ["Ctrl+Right"]; onActivated: win.seekRel(60) }
    Key { sequences: [","]; onActivated: { player.command(["frame-back-step"]); toast.show("Frame back", Icons.stepBackward) } }
    Key { sequences: ["."]; onActivated: { player.command(["frame-step"]); toast.show("Next frame", Icons.stepForward) } }
    Key { sequences: ["["]; onActivated: win.changeSpeed(-0.1) }
    Key { sequences: ["]"]; onActivated: win.changeSpeed(0.1) }
    Key { sequences: ["Backspace"]; onActivated: { player.setProp("speed", 1); toast.show("Normal speed", Icons.playSpeed) } }
    Key { sequences: ["Up", "Volume Up"]; onActivated: win.changeVolume(5) }
    Key { sequences: ["Down", "Volume Down"]; onActivated: win.changeVolume(-5) }
    Key { sequences: ["M", "Volume Mute"]; autoRepeat: false; onActivated: { player.cycle("mute"); toast.show(player.muted ? "Sound on" : "Muted", player.muted ? Icons.volumeHigh : Icons.volumeOff) } }
    Key { sequences: ["F", "F11", "Return"]; autoRepeat: false; onActivated: win.toggleFullscreen() }
    Key {
        sequences: ["Esc"]; autoRepeat: false
        onActivated: {
            if (shortcutsSheet.open) shortcutsSheet.open = false
            else if (urlDialog.open) urlDialog.open = false
            else if (win.panel !== "") win.panel = ""
            else if (win.fullscreen) win.toggleFullscreen()
        }
    }
    Key { sequences: ["Z"]; onActivated: win.nudgeSub(-0.1) }
    Key { sequences: ["X"]; onActivated: win.nudgeSub(0.1) }
    Key { sequences: ["Shift+Z"]; onActivated: win.nudgeSub(-1) }
    Key { sequences: ["Shift+X"]; onActivated: win.nudgeSub(1) }
    Key { sequences: ["G"]; onActivated: win.subStep(-1) }
    Key { sequences: ["H"]; onActivated: win.subStep(1) }
    Key { sequences: ["C"]; autoRepeat: false; onActivated: win.cycleTrack("sub") }
    Key { sequences: ["V"]; autoRepeat: false; onActivated: { player.cycle("sub-visibility"); toast.show(player.subVisible ? "Subtitles hidden" : "Subtitles shown", Icons.subtitles) } }
    Key { sequences: ["Ctrl+=", "Ctrl++"]; onActivated: { var v = Math.min(2.5, player.subScale + 0.1); player.setProp("sub-scale", v); prefs.subScale = v; toast.show("Subtitle size " + Math.round(v * 100) + "%", Icons.formatSize) } }
    Key { sequences: ["Ctrl+-"]; onActivated: { var v = Math.max(0.4, player.subScale - 0.1); player.setProp("sub-scale", v); prefs.subScale = v; toast.show("Subtitle size " + Math.round(v * 100) + "%", Icons.formatSize) } }
    Key { sequences: ["R"]; onActivated: { prefs.subPos = Math.max(50, prefs.subPos - 2); win.applySubPos(); toast.show("Subtitle position " + prefs.subPos + "%", Icons.arrowUp) } }
    Key { sequences: ["Shift+R"]; onActivated: { prefs.subPos = Math.min(100, prefs.subPos + 2); win.applySubPos(); toast.show("Subtitle position " + prefs.subPos + "%", Icons.arrowDown) } }
    Key { sequences: ["A"]; autoRepeat: false; onActivated: win.cycleTrack("audio") }
    Key { sequences: ["-"]; onActivated: win.nudgeAudio(-0.1) }
    Key { sequences: ["="]; onActivated: win.nudgeAudio(0.1) }
    Key { sequences: ["S"]; autoRepeat: false; onActivated: win.screenshot() }
    Key { sequences: ["I"]; autoRepeat: false; onActivated: win.togglePanel("info") }
    Key { sequences: ["T"]; autoRepeat: false; onActivated: win.togglePin() }
    Key { sequences: ["B"]; autoRepeat: false; onActivated: win.abLoop() }
    Key { sequences: ["Ctrl+R"]; autoRepeat: false; onActivated: win.toggleLoopFile() }
    Key { sequences: ["N", "Media Next"]; autoRepeat: false; onActivated: win.nextItem() }
    Key { sequences: ["P", "Media Previous"]; autoRepeat: false; onActivated: win.prevItem() }
    Key { sequences: ["PgUp"]; onActivated: { player.addProp("chapter", -1); toast.show("Previous chapter", Icons.skipPrevious) } }
    Key { sequences: ["PgDown"]; onActivated: { player.addProp("chapter", 1); toast.show("Next chapter", Icons.skipNext) } }
    Key { sequences: ["Home"]; onActivated: player.seekTo(0) }
    Key { sequences: ["Ctrl+O"]; autoRepeat: false; onActivated: { openDialog.appendMode = false; openDialog.open() } }
    Key { sequences: ["Ctrl+Shift+O"]; autoRepeat: false; onActivated: { folderDialog.appendMode = false; folderDialog.open() } }
    Key { sequences: ["Ctrl+U"]; autoRepeat: false; onActivated: urlDialog.open = true }
    Key { sequences: ["Ctrl+Shift+S"]; autoRepeat: false; onActivated: subDialog.open() }
    Key { sequences: ["Ctrl+1"]; autoRepeat: false; onActivated: win.togglePanel("subs") }
    Key { sequences: ["Ctrl+2"]; autoRepeat: false; onActivated: win.togglePanel("audio") }
    Key { sequences: ["Ctrl+3"]; autoRepeat: false; onActivated: win.togglePanel("video") }
    Key { sequences: ["Ctrl+4", "Ctrl+P"]; autoRepeat: false; onActivated: win.togglePanel("playlist") }
    Key { sequences: ["Ctrl+5"]; autoRepeat: false; onActivated: win.togglePanel("settings") }
    Key { sequences: ["F1"]; autoRepeat: false; onActivated: shortcutsSheet.open = !shortcutsSheet.open }
    Key { sequences: ["Ctrl+Q"]; autoRepeat: false; onActivated: win.close() }
    Repeater {
        model: 10
        delegate: Item {
            required property int index
            Shortcut {
                sequence: "" + index
                context: Qt.ApplicationShortcut
                onActivated: if (player.duration > 0) { player.seekTo(player.duration * index / 10); toast.show(index * 10 + "%", Icons.skipForward) }
            }
        }
    }
}
