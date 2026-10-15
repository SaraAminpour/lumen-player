"""Lumen — a modern player built on libmpv."""
import locale
import os
import re
import sys
import traceback

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
IS_WIN = sys.platform == "win32"
APP_ID = "SaraAminpour.Lumen.Player.1"
VERSION = "1.0.0"

if IS_WIN:
    os.environ["PATH"] = ROOT + os.pathsep + os.environ.get("PATH", "")
    try:
        os.add_dll_directory(ROOT)
    except Exception:
        pass

TEST_MODE = any(a.startswith("--test-shot") for a in sys.argv)
CONFIG_DIR = os.path.join(os.environ.get("APPDATA") or os.path.expanduser("~/.config"),
                          "Lumen-test" if TEST_MODE else "Lumen")
os.makedirs(CONFIG_DIR, exist_ok=True)
LOG_PATH = os.path.join(CONFIG_DIR, "lumen.log")

if sys.stderr is None or IS_WIN:
    _log = open(LOG_PATH, "w", encoding="utf-8", buffering=1)
    sys.stdout = sys.stderr = _log


def _fatal(text):
    sys.stderr.write(text)
    if IS_WIN:
        import ctypes
        ctypes.windll.user32.MessageBoxW(None, text[-1800:], "Lumen could not start", 0x10)


try:
    from PySide6.QtCore import (QObject, QUrl, Qt, Signal, Slot, QSettings, QCoreApplication,
                                QStandardPaths, QFileInfo, QDir, QTimer)
    from PySide6.QtGui import QGuiApplication, QIcon, QFontDatabase, QFont, QSurfaceFormat
    from PySide6.QtQml import QQmlApplicationEngine, qmlRegisterType
    from PySide6.QtQuick import QQuickWindow, QSGRendererInterface
    from PySide6.QtQuickControls2 import QQuickStyle
    import mpvcore
except Exception:
    _fatal(traceback.format_exc())
    raise SystemExit(1)

VIDEO_EXT = {"mkv", "mp4", "m4v", "avi", "mov", "webm", "wmv", "flv", "ts", "m2ts", "mts", "mpg",
             "mpeg", "ogv", "3gp", "vob", "rmvb", "divx", "f4v", "hevc", "y4m"}
AUDIO_EXT = {"mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "alac", "ape", "mka"}
SUB_EXT = {"srt", "ass", "ssa", "vtt", "sub", "sup", "idx", "smi", "lrc"}


def natural_key(s):
    return [int(t) if t.isdigit() else t.lower() for t in re.split(r"(\d+)", s)]


class Bridge(QObject):
    """Small helpers QML can't do on its own."""
    openRequested = Signal("QVariantList")

    def __init__(self, app):
        super().__init__()
        self._app = app

    @Slot(str, result=str)
    def kind(self, url):
        path = QUrl(url).toLocalFile() if url.startswith("file:") else url
        ext = os.path.splitext(path)[1].lower().lstrip(".")
        if ext in SUB_EXT:
            return "sub"
        if ext in AUDIO_EXT:
            return "audio"
        if ext in VIDEO_EXT:
            return "video"
        if os.path.isdir(path):
            return "dir"
        return "other"

    @Slot(str, result="QVariantList")
    def siblings(self, url):
        """Media files in the same folder, naturally sorted (for auto playlists)."""
        path = QUrl(url).toLocalFile() if url.startswith("file:") else url
        if not os.path.isfile(path):
            return []
        folder = os.path.dirname(path)
        ext = os.path.splitext(path)[1].lower().lstrip(".")
        group = VIDEO_EXT if ext in VIDEO_EXT else AUDIO_EXT if ext in AUDIO_EXT else {ext}
        try:
            names = [n for n in os.listdir(folder)
                     if os.path.splitext(n)[1].lower().lstrip(".") in group]
        except OSError:
            return []
        return [os.path.join(folder, n) for n in sorted(names, key=natural_key)]

    @Slot(str, result="QVariantList")
    def folderMedia(self, url):
        path = QUrl(url).toLocalFile() if url.startswith("file:") else url
        out = []
        for base, _dirs, files in os.walk(path):
            for n in files:
                if os.path.splitext(n)[1].lower().lstrip(".") in VIDEO_EXT | AUDIO_EXT:
                    out.append(os.path.join(base, n))
        return sorted(out, key=natural_key)

    @Slot(str, result=bool)
    def exists(self, path):
        return "://" in path or os.path.exists(path)

    @Slot(str, result=str)
    def baseName(self, path):
        if "://" in path:
            return path.rstrip("/").rsplit("/", 1)[-1] or path
        return os.path.basename(path)

    @Slot(str, result=str)
    def dirName(self, path):
        return "" if "://" in path else os.path.dirname(path)

    @Slot(str, result=str)
    def toUrl(self, path):
        return QUrl.fromLocalFile(path).toString() if "://" not in path else path

    @Slot(str)
    def reveal(self, path):
        if IS_WIN and os.path.exists(path):
            import subprocess
            subprocess.Popen(["explorer", "/select,", os.path.normpath(path)])
        elif os.path.exists(path):
            from PySide6.QtGui import QDesktopServices
            QDesktopServices.openUrl(QUrl.fromLocalFile(os.path.dirname(path)))

    @Slot(str)
    def openFolder(self, path):
        from PySide6.QtGui import QDesktopServices
        os.makedirs(path, exist_ok=True)
        QDesktopServices.openUrl(QUrl.fromLocalFile(path))

    @Slot(result=str)
    def clipboardText(self):
        return QGuiApplication.clipboard().text().strip()

    @Slot(str)
    def copyText(self, t):
        QGuiApplication.clipboard().setText(t)

    @Slot(result=str)
    def screenshotDir(self):
        return os.path.join(os.path.expanduser("~"), "Pictures", "Lumen Screenshots")

    @Slot(result=str)
    def version(self):
        return VERSION

    @Slot(result=bool)
    def isWindows(self):
        return IS_WIN

    @Slot(result=str)
    def registerOpenWith(self):
        """Adds Lumen to Explorer's "Open with" list for common media types (per-user, no admin)."""
        if not IS_WIN:
            return "Only available on Windows"
        try:
            import winreg
            exe = os.path.join(os.path.dirname(ROOT), "Lumen.exe")
            if not os.path.exists(exe):
                return "Lumen.exe not found next to the app folder"
            base = r"Software\Classes\Applications\Lumen.exe"
            with winreg.CreateKey(winreg.HKEY_CURRENT_USER, base) as k:
                winreg.SetValueEx(k, "FriendlyAppName", 0, winreg.REG_SZ, "Lumen Player")
            with winreg.CreateKey(winreg.HKEY_CURRENT_USER, base + r"\DefaultIcon") as k:
                winreg.SetValueEx(k, "", 0, winreg.REG_SZ, f'"{exe}",0')
            with winreg.CreateKey(winreg.HKEY_CURRENT_USER, base + r"\shell\open\command") as k:
                winreg.SetValueEx(k, "", 0, winreg.REG_SZ, f'"{exe}" "%1"')
            with winreg.CreateKey(winreg.HKEY_CURRENT_USER, base + r"\SupportedTypes") as k:
                for ext in sorted(VIDEO_EXT | AUDIO_EXT):
                    winreg.SetValueEx(k, "." + ext, 0, winreg.REG_SZ, "")
            for ext in sorted(VIDEO_EXT | AUDIO_EXT):
                with winreg.CreateKey(winreg.HKEY_CURRENT_USER,
                                      rf"Software\Classes\.{ext}\OpenWithList\Lumen.exe"):
                    pass
            import ctypes
            ctypes.windll.shell32.SHChangeNotify(0x08000000, 0, None, None)
            return "ok"
        except Exception as e:
            traceback.print_exc()
            return str(e)

    @Slot(QObject, bool)
    def styleWindow(self, window, fullscreen):
        style_window(window, fullscreen)

    @Slot(str)
    def log(self, msg):
        print(msg, flush=True)

    @Slot(QObject, bool, result=bool)
    def setFullscreen(self, window, on):
        """Windows fullscreen without the driver's exclusive-mode switch.

        A GL window that exactly covers the monitor gets promoted to exclusive fullscreen, which
        blanks/freezes the screen for seconds on hybrid-GPU laptops. Instead we keep a normal
        borderless window and size it 1px taller than the monitor. Returns False off Windows."""
        if not IS_WIN:
            return False
        import ctypes
        from ctypes import wintypes
        u = ctypes.windll.user32
        hwnd = wintypes.HWND(int(window.winId()))
        SWP = 0x0004 | 0x0010 | 0x0020    # NOZORDER | NOACTIVATE | FRAMECHANGED
        if on:   # QML un-maximizes first (Qt handles maximize of frameless windows properly)
            r = wintypes.RECT()
            u.GetWindowRect(hwnd, ctypes.byref(r))
            self._fs_saved = (r.left, r.top, r.right - r.left, r.bottom - r.top)

            class MONITORINFO(ctypes.Structure):
                _fields_ = [("cbSize", wintypes.DWORD), ("rcMonitor", wintypes.RECT),
                            ("rcWork", wintypes.RECT), ("dwFlags", wintypes.DWORD)]
            u.MonitorFromWindow.restype = wintypes.HMONITOR
            mon = u.MonitorFromWindow(hwnd, 2)   # MONITOR_DEFAULTTONEAREST
            mi = MONITORINFO()
            mi.cbSize = ctypes.sizeof(MONITORINFO)
            u.GetMonitorInfoW(wintypes.HMONITOR(mon), ctypes.byref(mi))
            m = mi.rcMonitor
            style_window(window, True)
            u.SetWindowPos(hwnd, None, m.left, m.top, m.right - m.left, m.bottom - m.top + 1, SWP)
            print("fullscreen on", self._fs_saved, (m.left, m.top, m.right, m.bottom), flush=True)
        else:
            x, y, w, h = getattr(self, "_fs_saved", (100, 100, 1280, 760))
            style_window(window, False)
            u.SetWindowPos(hwnd, None, x, y, w, h, SWP)
            print("fullscreen off", (x, y, w, h), flush=True)
        return True


def style_window(window, fullscreen=False):
    """Windows 11: rounded corners + dark frame shadow for the frameless window.
    In fullscreen the extended frame and the 1px Win11 border are removed (they show as a white edge)."""
    if not IS_WIN:
        return
    try:
        import ctypes
        hwnd = int(window.winId())
        dwm = ctypes.windll.dwmapi

        def attr(a, v):
            val = ctypes.c_uint(v)
            dwm.DwmSetWindowAttribute(hwnd, a, ctypes.byref(val), 4)
        attr(20, 1)                                   # DWMWA_USE_IMMERSIVE_DARK_MODE
        attr(33, 1 if fullscreen else 2)              # DWMWA_WINDOW_CORNER_PREFERENCE: DONOTROUND / ROUND
        attr(34, 0xFFFFFFFE if fullscreen else 0xFFFFFFFF)   # DWMWA_BORDER_COLOR: none / default

        class MARGINS(ctypes.Structure):
            _fields_ = [("l", ctypes.c_int), ("r", ctypes.c_int), ("t", ctypes.c_int), ("b", ctypes.c_int)]
        n = 0 if fullscreen else 1
        m = MARGINS(n, n, n, n)
        dwm.DwmExtendFrameIntoClientArea(hwnd, ctypes.byref(m))
    except Exception:
        traceback.print_exc()


INSTANCE_KEY = "LumenPlayer-" + (os.environ.get("USERNAME") or os.environ.get("USER") or "user")


def forward_to_running_instance(files):
    """If Lumen is already open, hand it our files and return True."""
    from PySide6.QtNetwork import QLocalSocket
    import json
    sock = QLocalSocket()
    sock.connectToServer(INSTANCE_KEY)
    if not sock.waitForConnected(250):
        return False
    if IS_WIN:
        try:
            import ctypes
            ctypes.windll.user32.AllowSetForegroundWindow(-1)
        except Exception:
            pass
    sock.write(json.dumps(files).encode("utf-8"))
    sock.flush()
    sock.waitForBytesWritten(1000)
    sock.disconnectFromServer()
    return True


def start_instance_server(bridge):
    from PySide6.QtNetwork import QLocalServer
    import json
    QLocalServer.removeServer(INSTANCE_KEY)
    server = QLocalServer()
    server.setSocketOptions(QLocalServer.UserAccessOption)
    server.listen(INSTANCE_KEY)

    def on_conn():
        conn = server.nextPendingConnection()
        buf = bytearray()

        def read():
            buf.extend(bytes(conn.readAll()))

        def done():
            read()
            try:
                files = json.loads(buf.decode("utf-8") or "[]")
            except Exception:
                files = []
            bridge.openRequested.emit(files)
            conn.deleteLater()
        conn.readyRead.connect(read)
        conn.disconnected.connect(done)
    server.newConnection.connect(on_conn)
    return server


def main():
    locale.setlocale(locale.LC_NUMERIC, "C")
    import installer
    if IS_WIN and "--uninstall" in sys.argv:
        return installer.run_uninstaller()
    if installer.launched_as_installer():
        QQuickStyle.setStyle("Basic")
        return installer.run_installer(VIDEO_EXT | AUDIO_EXT, VERSION,
                                       os.path.join(HERE, "assets", "InterVariable.ttf"), style_window)
    if IS_WIN:
        try:
            import ctypes
            ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID(APP_ID)
        except Exception:
            pass

    QQuickWindow.setGraphicsApi(QSGRendererInterface.GraphicsApi.OpenGL)
    fmt = QSurfaceFormat.defaultFormat()
    fmt.setSwapInterval(1)
    QSurfaceFormat.setDefaultFormat(fmt)
    QGuiApplication.setHighDpiScaleFactorRoundingPolicy(Qt.HighDpiScaleFactorRoundingPolicy.PassThrough)
    QCoreApplication.setOrganizationName("LumenTest" if TEST_MODE else "Lumen")
    QCoreApplication.setApplicationName("Lumen")
    QQuickStyle.setStyle("Basic")

    app = QGuiApplication(sys.argv)
    locale.setlocale(locale.LC_NUMERIC, "C")
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    files = [os.path.abspath(a) if os.path.exists(a) else a for a in args]
    if "--new-window" not in sys.argv and not any(a.startswith("--test") for a in sys.argv):
        if forward_to_running_instance(files):
            return 0
    app.setWindowIcon(QIcon(os.path.join(HERE, "assets", "lumen.png")))
    fid = QFontDatabase.addApplicationFont(os.path.join(HERE, "assets", "InterVariable.ttf"))
    families = QFontDatabase.applicationFontFamilies(fid) if fid >= 0 else []
    ui_family = families[0] if families else ("Segoe UI Variable Text" if IS_WIN else "Sans")
    f = QFont(ui_family, 10)
    f.setHintingPreference(QFont.PreferNoHinting)
    app.setFont(f)

    qmlRegisterType(mpvcore.VideoItem, "Lumen", 1, 0, "VideoItem")

    player = mpvcore.Player(CONFIG_DIR)
    player.logMessage.connect(lambda m: print(m, flush=True))
    if TEST_MODE:   # silent, isolated smoke test
        player.mpv["ao"] = "null"
        player.mpv["save-position-on-quit"] = "no"
    mpvcore.VideoItem.default_player = player
    bridge = Bridge(app)
    _server = start_instance_server(bridge)  # noqa: F841 (kept alive for the app's lifetime)
    provider = mpvcore.ThumbProvider()
    thumbs = mpvcore.Thumbnailer(provider)

    engine = QQmlApplicationEngine()
    engine.addImageProvider("thumb", provider)
    ctx = engine.rootContext()
    ctx.setContextProperty("player", player)
    ctx.setContextProperty("bridge", bridge)
    ctx.setContextProperty("thumbs", thumbs)
    ctx.setContextProperty("uiFont", ui_family)
    ctx.setContextProperty("isWindows", IS_WIN)
    ctx.setContextProperty("startupFiles", files)
    ctx.setContextProperty("logPath", LOG_PATH)
    acts = [a.split("=", 1)[1] for a in sys.argv if a.startswith("--act=")]
    shot = next((a.split("=", 1)[1] for a in sys.argv if a.startswith("--test-shot=")), "")
    ctx.setContextProperty("testShot", shot)
    ctx.setContextProperty("testActions", acts)
    engine.load(QUrl.fromLocalFile(os.path.join(HERE, "qml", "Main.qml")))
    if not engine.rootObjects():
        _fatal("QML failed to load. See " + LOG_PATH)
        return 1

    rc = app.exec()
    thumbs.stop()
    player.shutdown()
    return rc


if __name__ == "__main__":
    try:
        rc = main()
        sys.stdout.flush()
        os._exit(rc or 0)   # skip interpreter teardown (Qt already owns/destroyed GL objects)
    except SystemExit:
        raise
    except Exception:
        _fatal(traceback.format_exc())
        sys.exit(1)
