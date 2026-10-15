import QtQuick
import QtQuick.Controls.Basic
import "."

ToolTip {
    id: tip
    delay: 450
    timeout: 4000
    padding: 0
    topPadding: 6; bottomPadding: 6; leftPadding: 10; rightPadding: 10
    font.family: Theme.font
    font.pixelSize: 12
    contentItem: Text {
        text: tip.text
        font: tip.font
        color: Theme.text
    }
    background: Rectangle {
        radius: 8
        color: "#1b1e27"
        border.color: Theme.glassBorder
    }
    enter: Transition {
        ParallelAnimation {
            NumberAnimation { property: "opacity"; from: 0; to: 1; duration: 140; easing.type: Easing.OutCubic }
            NumberAnimation { property: "scale"; from: 0.92; to: 1; duration: 180; easing.type: Easing.OutBack }
        }
    }
    exit: Transition { NumberAnimation { property: "opacity"; to: 0; duration: 100 } }
}
