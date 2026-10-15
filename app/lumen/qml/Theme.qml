pragma Singleton
import QtQuick

QtObject {
    id: t
    property string font: "Inter"
    property int accentIndex: 0
    readonly property var accents: [
        { name: "Aurora",  a: "#8b5cf6", b: "#22d3ee" },
        { name: "Ocean",   a: "#2563eb", b: "#2dd4bf" },
        { name: "Sunset",  a: "#f97316", b: "#ec4899" },
        { name: "Mint",    a: "#10b981", b: "#a3e635" },
        { name: "Rose",    a: "#e11d48", b: "#f59e0b" },
        { name: "Mono",    a: "#e5e7eb", b: "#9ca3af" }
    ]
    readonly property color accentA: accents[accentIndex].a
    readonly property color accentB: accents[accentIndex].b
    readonly property color accent: Qt.lighter(accentA, 1.25)
    readonly property color onAccent: accentIndex === 5 ? "#0b0c10" : "#ffffff"

    readonly property color bg: "#07080c"
    readonly property color glass: Qt.rgba(0.055, 0.06, 0.085, 0.74)
    readonly property color glassSolid: "#12141b"
    readonly property color glassBorder: Qt.rgba(1, 1, 1, 0.09)
    readonly property color card: Qt.rgba(1, 1, 1, 0.045)
    readonly property color cardHover: Qt.rgba(1, 1, 1, 0.08)
    readonly property color hover: Qt.rgba(1, 1, 1, 0.10)
    readonly property color press: Qt.rgba(1, 1, 1, 0.16)
    readonly property color text: "#f4f5fa"
    readonly property color textDim: Qt.rgba(0.96, 0.97, 1, 0.64)
    readonly property color textFaint: Qt.rgba(0.96, 0.97, 1, 0.40)
    readonly property color danger: "#f43f5e"
    readonly property color warn: "#f59e0b"
    readonly property color good: "#34d399"

    readonly property int fast: 160
    readonly property int med: 260
    readonly property int slow: 420

    function pad(n) { return n < 10 ? "0" + n : "" + n }
    function fmtTime(s) {
        if (s === undefined || s === null || isNaN(s) || s < 0) s = 0
        s = Math.floor(s)
        var h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60
        return h > 0 ? h + ":" + pad(m) + ":" + pad(sec) : m + ":" + pad(sec)
    }
    function fmtDelay(v) {
        var ms = Math.round(v * 1000)
        return (ms > 0 ? "+" : ms < 0 ? "−" : "±") + (Math.abs(ms) / 1000).toFixed(3) + " s"
    }
    function fmtBytes(b) {
        if (!b) return "—"
        var u = ["B", "KB", "MB", "GB", "TB"], i = 0
        while (b >= 1024 && i < u.length - 1) { b /= 1024; i++ }
        return b.toFixed(i > 1 ? 2 : 0) + " " + u[i]
    }
    function fmtRate(b) {
        if (!b) return "—"
        return b >= 1e6 ? (b / 1e6).toFixed(2) + " Mb/s" : Math.round(b / 1e3) + " kb/s"
    }
}
