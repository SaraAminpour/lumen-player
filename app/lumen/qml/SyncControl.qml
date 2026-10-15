import QtQuick
import QtQuick.Layouts
import "."
import "Icons.js" as Icons

// Delay editor: big live readout, nudge buttons, fine slider, reset.
Column {
    id: root
    property real value: 0
    property string earlyText: "earlier"
    property string lateText: "later"
    signal setTo(real v)
    width: parent ? parent.width : 300
    spacing: 12

    onValueChanged: bump.restart()

    Item {
        width: parent.width
        height: 52
        Column {
            anchors.verticalCenter: parent.verticalCenter
            spacing: 2
            Text {
                id: readout
                text: Theme.fmtDelay(root.value)
                font.family: Theme.font; font.pixelSize: 30; font.weight: Font.Bold
                font.features: { "tnum": 1 }
                color: Math.abs(root.value) < 0.0005 ? Theme.text : root.value > 0 ? Theme.accentB : Theme.warn
                Behavior on color { ColorAnimation { duration: Theme.med } }
                transformOrigin: Item.Left
                SequentialAnimation on scale {
                    id: bump
                    running: false
                    NumberAnimation { to: 1.08; duration: 90; easing.type: Easing.OutQuad }
                    NumberAnimation { to: 1.0; duration: 260; easing.type: Easing.OutBack }
                }
            }
            Text {
                text: Math.abs(root.value) < 0.0005 ? "In sync with the file's original timing"
                     : (Math.abs(root.value) * 1000).toFixed(0) + " ms " + (root.value > 0 ? root.lateText : root.earlyText)
                font.family: Theme.font; font.pixelSize: 11
                color: Theme.textFaint
            }
        }
        PillButton {
            anchors.right: parent.right
            anchors.verticalCenter: parent.verticalCenter
            icon: Icons.restore; text: "Reset"; h: 32; hpad: 12; fontSize: 12
            enabled: Math.abs(root.value) > 0.0005
            onClicked: root.setTo(0)
        }
    }
    RowLayout {
        width: parent.width
        spacing: 6
        Repeater {
            model: [-1, -0.1, -0.05, 0.05, 0.1, 1]
            delegate: Chip {
                Layout.fillWidth: true
                text: (modelData > 0 ? "+" : "−") + (Math.abs(modelData) >= 1 ? Math.abs(modelData) + " s" : Math.abs(modelData * 1000) + " ms")
                onClicked: root.setTo(Math.round((root.value + modelData) * 1000) / 1000)
            }
        }
    }
    Item {
        width: parent.width; height: 26
        Text { text: "−10 s"; color: Theme.textFaint; font.family: Theme.font; font.pixelSize: 10; anchors.verticalCenter: parent.verticalCenter }
        LSlider {
            anchors.left: parent.left; anchors.right: parent.right
            anchors.leftMargin: 36; anchors.rightMargin: 36
            anchors.verticalCenter: parent.verticalCenter
            from: -10; to: 10; stepSize: 0.05; centered: true; defaultValue: 0
            value: root.value
            onMoved: (v) => root.setTo(Math.round(v * 1000) / 1000)
        }
        Text { text: "+10 s"; color: Theme.textFaint; font.family: Theme.font; font.pixelSize: 10; anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter }
    }
}
