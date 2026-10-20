"""Check GitHub Releases for a newer Lumen and install it quietly.

The latest release is read from the GitHub API. "Install" downloads Lumen-Setup-<version>.exe,
checks its SHA-256 against the digest GitHub publishes for the asset, and starts it with /S.
The setup closes this window, swaps in the new version and reopens Lumen on the same video.
"""
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import threading
import time
import traceback
import urllib.request

from PySide6.QtCore import QObject, Property, Signal, Slot, QSettings, Qt

REPO = "SaraAminpour/lumen-player"
API = f"https://api.github.com/repos/{REPO}/releases/latest"
RELEASES_PAGE = f"https://github.com/{REPO}/releases/latest"
SETUP_RE = re.compile(r"^Lumen-Setup-[\d.]+\.exe$", re.I)
AUTO_EVERY = 20 * 3600            # automatic checks at most this often (seconds)
IS_WIN = sys.platform == "win32"
DETACHED = 0x00000008 | 0x00000200 | 0x08000000   # DETACHED_PROCESS | NEW_PROCESS_GROUP | NO_WINDOW


def parse_version(v):
    return tuple(int(x) for x in re.findall(r"\d+", v or "")[:3]) or (0,)


def _get(url, timeout=20):
    req = urllib.request.Request(url, headers={"User-Agent": "Lumen-Player-Updater",
                                               "Accept": "application/vnd.github+json"})
    return urllib.request.urlopen(req, timeout=timeout)


class Updater(QObject):
    changed = Signal()
    _step = Signal(str, float, str)          # state, progress, message (from worker threads)
    readyToInstall = Signal()                # QML saves the playback position, then calls launchSetup()

    def __init__(self, version, parent=None):
        super().__init__(parent)
        # LUMEN_UPDATER_TEST=<old version> exercises the flow off Windows (download only)
        self.version = os.environ.get("LUMEN_UPDATER_TEST") or version
        self._state = "idle"     # idle checking uptodate available downloading ready installing error
        self._progress = 0.0
        self._message = ""
        self._latest = ""
        self._notes = ""
        self._asset = None
        self._file = ""
        self._auto = False       # last check was automatic (UI shows a banner instead of a toast)
        self._settings = QSettings()
        self._step.connect(self._apply, Qt.QueuedConnection)

    def _apply(self, state, progress, message):
        self._state, self._progress, self._message = state, progress, message
        if state in ("error", "available", "uptodate", "ready"):
            print(f"updater: {state} {self._latest} {message}", flush=True)
        self.changed.emit()
        if state == "ready":
            self.readyToInstall.emit()

    state = Property(str, lambda s: s._state, notify=changed)
    progress = Property(float, lambda s: s._progress, notify=changed)
    message = Property(str, lambda s: s._message, notify=changed)
    latest = Property(str, lambda s: s._latest, notify=changed)
    current = Property(str, lambda s: s.version, constant=True)
    notes = Property(str, lambda s: s._notes, notify=changed)
    automatic = Property(bool, lambda s: s._auto, notify=changed)
    supported = Property(bool, lambda s: IS_WIN or bool(os.environ.get("LUMEN_UPDATER_TEST")), constant=True)
    releasesPage = Property(str, lambda s: RELEASES_PAGE, constant=True)

    @Slot(bool)
    def check(self, automatic=False):
        if self._state in ("checking", "downloading", "ready", "installing"):
            return
        if automatic:
            last = float(self._settings.value("update/lastCheck", 0) or 0)
            if time.time() - last < AUTO_EVERY:
                return
        self._auto = automatic
        self._step.emit("checking", 0.0, "Checking for updates…")
        threading.Thread(target=self._check, daemon=True).start()

    def _check(self):
        try:
            with _get(API) as r:
                rel = json.load(r)
            self._settings.setValue("update/lastCheck", time.time())
            tag = rel.get("tag_name", "")
            self._latest = tag.lstrip("vV")
            self._notes = _short_notes(rel.get("body") or "")
            self._asset = next((a for a in rel.get("assets", []) if SETUP_RE.match(a.get("name", ""))), None)
            if parse_version(self._latest) <= parse_version(self.version):
                self._step.emit("uptodate", 0.0, f"Lumen {self.version} is the latest version.")
            elif not self._asset:
                self._step.emit("error", 0.0, f"Lumen {self._latest} is out, but its installer isn't uploaded yet.")
            else:
                mb = self._asset.get("size", 0) / 1e6
                self._step.emit("available", 0.0, f"Lumen {self._latest} is available ({mb:.0f} MB).")
        except Exception as e:
            traceback.print_exc()
            self._step.emit("error", 0.0, "Couldn't reach GitHub. Check your internet connection." if
                            isinstance(e, OSError) else f"Update check failed: {e}")

    @Slot()
    def install(self):
        if self._state not in ("available", "error") or not self._asset:
            return
        self._step.emit("downloading", 0.0, "Downloading…")
        threading.Thread(target=self._download, daemon=True).start()

    def _download(self):
        a = self._asset
        try:
            folder = os.path.join(tempfile.gettempdir(), "LumenUpdate")
            os.makedirs(folder, exist_ok=True)
            out = os.path.join(folder, a["name"])
            total = a.get("size") or 0
            h = hashlib.sha256()
            with _get(a["browser_download_url"], timeout=60) as r, open(out + ".part", "wb") as f:
                done, last = 0, 0.0
                while True:
                    chunk = r.read(1 << 18)
                    if not chunk:
                        break
                    f.write(chunk)
                    h.update(chunk)
                    done += len(chunk)
                    if total and time.time() - last > 0.15:
                        last = time.time()
                        self._step.emit("downloading", done / total,
                                        f"Downloading…  {done / 1e6:.0f} / {total / 1e6:.0f} MB")
            if total and done != total:
                raise RuntimeError("The download was incomplete. Please try again.")
            digest = (a.get("digest") or "").lower()
            if digest.startswith("sha256:") and digest[7:] != h.hexdigest():
                raise RuntimeError("The download was damaged (checksum mismatch). Please try again.")
            os.replace(out + ".part", out)
            self._file = out
            self._step.emit("ready", 1.0, f"Installing Lumen {self._latest}…")
        except Exception as e:
            traceback.print_exc()
            self._step.emit("error", 0.0, str(e) if isinstance(e, RuntimeError) else
                            "The download failed. Check your internet connection and try again.")

    @Slot(str)
    def launchSetup(self, reopen):
        """Start the downloaded setup quietly. It closes this window, installs, and reopens Lumen."""
        if not self._file or not IS_WIN:
            return
        args = [self._file, "/S", "--update", "--relaunch"]
        if reopen:
            args.append(reopen)
        try:
            subprocess.Popen(args, creationflags=DETACHED, close_fds=True, cwd=os.path.dirname(self._file))
            self._step.emit("installing", 1.0, f"Installing Lumen {self._latest}. Lumen will reopen by itself.")
        except OSError as e:
            self._step.emit("error", 0.0, f"Couldn't start the installer: {e}")


def _short_notes(body, limit=7):
    """First bullet points of the release notes, as plain text lines."""
    lines = []
    for raw in body.splitlines():
        s = raw.strip()
        if s.startswith(("- ", "* ")):
            s = re.sub(r"\*+|`|\[([^\]]+)\]\([^)]+\)", lambda m: m.group(1) or "", s[2:])
            lines.append("•  " + s)
        if len(lines) >= limit:
            break
    return "\n".join(lines)


def update_ytdlp(exe, stamp_dir, every=3 * 86400):
    """Keep the bundled yt-dlp fresh (YouTube changes often). Runs hidden, at most every 3 days."""
    stamp = os.path.join(stamp_dir, "yt-dlp.updated")
    try:
        if not os.path.exists(exe) or time.time() - os.path.getmtime(stamp) < every:
            return
    except OSError:
        pass

    def run():
        try:
            subprocess.run([exe, "-U"], creationflags=0x08000000 if IS_WIN else 0, stdin=subprocess.DEVNULL,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=180)
            with open(stamp, "w") as f:
                f.write(str(time.time()))
        except Exception:
            traceback.print_exc()
    threading.Thread(target=run, daemon=True).start()
