"""Per-user install / uninstall for the portable Lumen folder (no admin rights needed).

"Install Lumen.exe" (a copy of the launcher) or `Lumen.exe --install` copies the folder to
%LOCALAPPDATA%\\Programs\\Lumen, adds Desktop + Start Menu shortcuts, the Explorer "Open with"
entry and an Apps & Features entry. `Lumen.exe --uninstall` undoes all of it.
"""
import os
import shutil
import subprocess
import sys
import threading
import time
import traceback

from PySide6.QtCore import QObject, Property, Signal, Slot, QUrl, Qt
from PySide6.QtGui import QGuiApplication, QIcon, QFontDatabase, QFont
from PySide6.QtQml import QQmlApplicationEngine

IS_WIN = sys.platform == "win32"
HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.environ.get("LUMEN_INSTALL_SRC") or os.path.dirname(os.path.dirname(HERE))  # folder holding Lumen.exe and app\
NO_WINDOW = 0x08000000                                 # CREATE_NO_WINDOW: no console pops up
UNINSTALL_KEY = r"Software\Microsoft\Windows\CurrentVersion\Uninstall\LumenPlayer"
HOMEPAGE = "https://github.com/SaraAminpour/lumen-player"
SKIP = {"install lumen.exe"}


def install_dir():
    if os.environ.get("LUMEN_INSTALL_DIR"):
        return os.environ["LUMEN_INSTALL_DIR"]
    base = os.environ.get("LOCALAPPDATA") or os.path.expanduser("~/.local/share")
    return os.path.join(base, "Programs", "Lumen")


def launched_as_installer():
    if "--install" in sys.argv:
        return True
    if not IS_WIN:
        return False
    import ctypes
    buf = ctypes.create_unicode_buffer(1024)
    ctypes.windll.kernel32.GetModuleFileNameW(None, buf, 1024)
    return os.path.basename(buf.value).lower().startswith("install")


def _hidden(cmd, timeout=60):
    try:
        return subprocess.run(cmd, creationflags=NO_WINDOW if IS_WIN else 0, stdin=subprocess.DEVNULL,
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=timeout)
    except Exception:
        traceback.print_exc()


def _close_running(dest):
    """Close Lumen windows running from `dest` (politely first, so they save the playback position)."""
    ps = ("Get-Process Lumen -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '%s\\*' } | "
          "ForEach-Object { [void]$_.CloseMainWindow(); if (-not $_.WaitForExit(4000)) { $_.Kill(); [void]$_.WaitForExit(5000) } }"
          % dest.replace("'", "''"))
    _hidden(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-Command", ps], timeout=30)


def _shortcut_paths():
    def known(name):
        r = subprocess.run(["powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command",
                            f"[Environment]::GetFolderPath('{name}')"],
                           creationflags=NO_WINDOW, stdin=subprocess.DEVNULL, capture_output=True, text=True, timeout=60)
        return r.stdout.strip()
    return [os.path.join(known("Desktop"), "Lumen Player.lnk"),
            os.path.join(known("Programs"), "Lumen Player.lnk")]


def _make_shortcuts(exe):
    ps = ["$ws = New-Object -ComObject WScript.Shell"]
    for d in ("Desktop", "Programs"):
        ps.append(f"$s = $ws.CreateShortcut((Join-Path ([Environment]::GetFolderPath('{d}')) 'Lumen Player.lnk'))")
        ps.append(f"$s.TargetPath = '{exe}'; $s.WorkingDirectory = '{os.path.dirname(exe)}'; "
                  f"$s.IconLocation = '{exe},0'; $s.Description = 'Lumen Player'; $s.Save()")
    _hidden(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
             "-Command", "; ".join(ps)])


def register_open_with(exe, media_exts):
    import winreg
    base = r"Software\Classes\Applications\Lumen.exe"
    with winreg.CreateKey(winreg.HKEY_CURRENT_USER, base) as k:
        winreg.SetValueEx(k, "FriendlyAppName", 0, winreg.REG_SZ, "Lumen Player")
    with winreg.CreateKey(winreg.HKEY_CURRENT_USER, base + r"\DefaultIcon") as k:
        winreg.SetValueEx(k, "", 0, winreg.REG_SZ, f'"{exe}",0')
    with winreg.CreateKey(winreg.HKEY_CURRENT_USER, base + r"\shell\open\command") as k:
        winreg.SetValueEx(k, "", 0, winreg.REG_SZ, f'"{exe}" "%1"')
    with winreg.CreateKey(winreg.HKEY_CURRENT_USER, base + r"\SupportedTypes") as k:
        for ext in media_exts:
            winreg.SetValueEx(k, "." + ext, 0, winreg.REG_SZ, "")
    for ext in media_exts:
        with winreg.CreateKey(winreg.HKEY_CURRENT_USER, rf"Software\Classes\.{ext}\OpenWithList\Lumen.exe"):
            pass
    import ctypes
    ctypes.windll.shell32.SHChangeNotify(0x08000000, 0, None, None)


def _register_uninstall(dest, version, size_kb):
    import winreg
    exe = os.path.join(dest, "Lumen.exe")
    with winreg.CreateKey(winreg.HKEY_CURRENT_USER, UNINSTALL_KEY) as k:
        for name, val in (("DisplayName", "Lumen Player"), ("DisplayVersion", version),
                          ("Publisher", "Sara Aminpour"), ("DisplayIcon", f'"{exe}",0'),
                          ("InstallLocation", dest), ("UninstallString", f'"{exe}" --uninstall'),
                          ("QuietUninstallString", f'"{exe}" --uninstall --quiet'),
                          ("URLInfoAbout", HOMEPAGE), ("HelpLink", HOMEPAGE + "/issues")):
            winreg.SetValueEx(k, name, 0, winreg.REG_SZ, val)
        winreg.SetValueEx(k, "EstimatedSize", 0, winreg.REG_DWORD, int(size_kb))
        winreg.SetValueEx(k, "NoModify", 0, winreg.REG_DWORD, 1)
        winreg.SetValueEx(k, "NoRepair", 0, winreg.REG_DWORD, 1)


class Installer(QObject):
    changed = Signal()
    _step = Signal(float, str, str)     # progress, state, message (emitted from the copy thread)

    def __init__(self, media_exts, version, auto=False, relaunch=None, updating=False):
        super().__init__()
        self._relaunch = relaunch      # args to reopen Lumen with once installed (in-app updates)
        self._updating = updating
        self._exts = sorted(media_exts)
        self._version = version
        self._auto = auto
        self._progress = 0.0
        self._state = "ready"
        self._message = ""
        self.dest = install_dir()
        self._step.connect(self._apply, Qt.QueuedConnection)

    def _apply(self, p, state, msg):
        if state != self._state or p in (0.0, 1.0):
            print(f"installer: {state} {p:.2f} {msg}", flush=True)
        self._progress, self._state, self._message = p, state, msg
        self.changed.emit()
        if state == "done" and self._relaunch is not None:
            exe = os.path.join(self.dest, "Lumen.exe")
            if IS_WIN and os.path.exists(exe):
                subprocess.Popen([exe] + self._relaunch, cwd=self.dest, close_fds=True)

    progress = Property(float, lambda s: s._progress, notify=changed)
    state = Property(str, lambda s: s._state, notify=changed)
    message = Property(str, lambda s: s._message, notify=changed)
    target = Property(str, lambda s: s.dest, constant=True)
    auto = Property(bool, lambda s: s._auto, constant=True)
    updating = Property(bool, lambda s: s._updating, constant=True)

    @Slot()
    def start(self):
        if self._state in ("ready", "error"):
            self._apply(0.0, "working", "Preparing…")
            threading.Thread(target=self._run, daemon=True).start()

    def _run(self):
        try:
            same = os.path.normcase(os.path.abspath(SRC)) == os.path.normcase(os.path.abspath(self.dest))
            if not same:
                files = []
                for root, dirs, names in os.walk(SRC):
                    for n in names:
                        if n.lower() not in SKIP:
                            files.append(os.path.relpath(os.path.join(root, n), SRC))
                # copy into a side folder first, so a failure never leaves a half-installed Lumen
                staging, old = self.dest + ".new", self.dest + ".old"
                shutil.rmtree(staging, ignore_errors=True)
                total = len(files)
                for i, rel in enumerate(files):
                    out = os.path.join(staging, rel)
                    os.makedirs(os.path.dirname(out), exist_ok=True)
                    shutil.copy2(os.path.join(SRC, rel), out)
                    if i % 15 == 0 or i == total - 1:
                        self._step.emit(0.85 * (i + 1) / total, "working", f"Copying files  ·  {i + 1} / {total}")
                if os.path.isdir(self.dest):
                    self._step.emit(0.87, "working", "Replacing the old version…")
                    if IS_WIN:
                        _close_running(self.dest)
                    shutil.rmtree(old, ignore_errors=True)
                    for attempt in range(30):
                        try:
                            os.rename(self.dest, old)
                            break
                        except OSError:
                            if attempt == 29:
                                raise RuntimeError("Lumen is still open. Close it and press Try again.")
                            time.sleep(0.5)
                os.rename(staging, self.dest)
                shutil.rmtree(old, ignore_errors=True)
            exe = os.path.join(self.dest, "Lumen.exe")
            if IS_WIN and not os.environ.get("LUMEN_TEST"):   # tests install to a scratch folder only
                self._step.emit(0.93, "working", "Adding shortcuts…")
                _make_shortcuts(exe)
                self._step.emit(0.97, "working", "Adding Lumen to “Open with”…")
                register_open_with(exe, self._exts)
                size = sum(os.path.getsize(os.path.join(r, n)) for r, _, ns in os.walk(self.dest) for n in ns)
                _register_uninstall(self.dest, self._version, size // 1024)
            self._step.emit(1.0, "done", "Lumen is on your Desktop and in the Start menu.")
        except Exception as e:
            traceback.print_exc()
            self._step.emit(self._progress, "error", str(e))

    @Slot()
    def launch(self):
        exe = os.path.join(self.dest, "Lumen.exe")
        if IS_WIN and os.path.exists(exe):
            subprocess.Popen([exe], cwd=self.dest, close_fds=True)
        QGuiApplication.quit()


def run_installer(media_exts, version, ui_font_path, style_window):
    QGuiApplication.setHighDpiScaleFactorRoundingPolicy(Qt.HighDpiScaleFactorRoundingPolicy.PassThrough)
    app = QGuiApplication(sys.argv)
    app.setWindowIcon(QIcon(os.path.join(HERE, "assets", "lumen.png")))
    fid = QFontDatabase.addApplicationFont(ui_font_path)
    fam = QFontDatabase.applicationFontFamilies(fid) if fid >= 0 else []
    family = fam[0] if fam else "Segoe UI"
    app.setFont(QFont(family, 10))
    relaunch = None
    if "--relaunch" in sys.argv:   # Lumen's own updater: reopen the video that was playing
        relaunch = [a for a in sys.argv[sys.argv.index("--relaunch") + 1:] if not a.startswith(("--", "/"))][:1]
    inst = Installer(media_exts, version, auto="--auto" in sys.argv, relaunch=relaunch, updating="--update" in sys.argv)
    engine = QQmlApplicationEngine()
    ctx = engine.rootContext()
    ctx.setContextProperty("installer", inst)
    ctx.setContextProperty("uiFont", family)
    ctx.setContextProperty("testShot", next((a.split("=", 1)[1] for a in sys.argv if a.startswith("--test-shot=")), ""))
    engine.load(QUrl.fromLocalFile(os.path.join(HERE, "qml", "Installer.qml")))
    if not engine.rootObjects():
        return 1
    win = engine.rootObjects()[0]
    if style_window:
        style_window(win)
    return app.exec()


def run_uninstaller():
    import ctypes
    dest = os.path.dirname(os.path.dirname(HERE))
    quiet = "--quiet" in sys.argv
    if not quiet and ctypes.windll.user32.MessageBoxW(None, "Remove Lumen Player from this PC?\n\nYour settings and "
                                                      "watch history are kept.", "Uninstall Lumen", 0x24) != 6:
        return 0
    import winreg
    _hidden(["taskkill", "/F", "/IM", "Lumen.exe", "/FI", f"PID ne {os.getpid()}"])
    for p in _shortcut_paths():
        try:
            os.remove(p)
        except OSError:
            pass

    def drop(key):
        try:
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, key) as k:
                while True:
                    try:
                        sub = winreg.EnumKey(k, 0)
                    except OSError:
                        break
                    drop(key + "\\" + sub)
            winreg.DeleteKey(winreg.HKEY_CURRENT_USER, key)
        except OSError:
            pass
    drop(r"Software\Classes\Applications\Lumen.exe")
    drop(UNINSTALL_KEY)
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Software\Classes") as k:
        exts = []
        i = 0
        while True:
            try:
                n = winreg.EnumKey(k, i)
            except OSError:
                break
            if n.startswith("."):
                exts.append(n)
            i += 1
    for e in exts:
        drop(rf"Software\Classes\{e}\OpenWithList\Lumen.exe")
    ctypes.windll.shell32.SHChangeNotify(0x08000000, 0, None, None)
    if not quiet:
        ctypes.windll.user32.MessageBoxW(None, "Lumen Player was removed.", "Uninstall Lumen", 0x40)
    # the folder can only go once this process has exited
    # a plain string: list2cmdline would escape the quotes as \" which cmd doesn't understand
    subprocess.Popen(f'cmd /c ping -n 3 127.0.0.1 >nul & rmdir /s /q "{dest}"',
                     creationflags=NO_WINDOW, cwd=os.environ.get("TEMP", "C:\\"), close_fds=True)
    return 0
