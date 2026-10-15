import QtQuick
import QtQuick.Controls.Basic
import "."

Menu {
    id: m
    topPadding: 6; bottomPadding: 6
    delegate: LMenuItem {}
    background: Rectangle {
        implicitWidth: 250
        radius: 14
        color: "#151821"
        border.color: Theme.glassBorder
    }
    enter: Transition {
        ParallelAnimation {
            NumberAnimation { property: "opacity"; from: 0; to: 1; duration: 140 }
            NumberAnimation { property: "scale"; from: 0.94; to: 1; duration: 220; easing.type: Easing.OutBack }
        }
    }
    exit: Transition { NumberAnimation { property: "opacity"; to: 0; duration: 90 } }
}
