# Third-party notices

Lumen Player is licensed under the GNU General Public License v3.0 or later (see `LICENSE`).
The Windows release bundles the following components, each under its own license:

| Component | Use in Lumen | License |
|---|---|---|
| [mpv / libmpv](https://mpv.io) (Windows build by [shinchiro](https://github.com/shinchiro/mpv-winbuild-cmake)), including FFmpeg and libass | Video and audio decoding, rendering, subtitles | GPL-2.0-or-later (this build) |
| [Qt 6](https://www.qt.io) and [PySide6](https://doc.qt.io/qtforpython-6/) 6.12 | User interface (Qt Quick / QML) | LGPL-3.0 |
| [python-mpv](https://github.com/jaseg/python-mpv) 1.0.8 | Python bindings for libmpv | GPL-2.0-or-later / LGPL-2.1-or-later |
| [CPython](https://www.python.org) 3.13 (embeddable package) | Runtime | PSF License (see `app/LICENSE.txt` in the bundle) |
| [Inter](https://rsms.me/inter/) typeface by Rasmus Andersson | UI font | SIL Open Font License 1.1 (`app/lumen/assets/Inter-LICENSE.txt`) |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | Opening YouTube and other web video links | Unlicense |
| [QuickJS-ng](https://github.com/quickjs-ng/quickjs) | JavaScript runtime yt-dlp uses for YouTube | MIT |
| [Material Design Icons](https://pictogrammers.com/library/mdi/) by Pictogrammers | Icon paths in `qml/Icons.js` | Apache-2.0 |

The source code of each component is available from the linked project pages.
