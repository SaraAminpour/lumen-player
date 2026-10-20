"""libmpv bridge for Lumen: the player controller, the QtQuick video item and the seek-bar thumbnailer."""
import ctypes
import os
import threading
import time

from PySide6.QtCore import (QObject, Property, QSize, QThread, Qt, QTimer, QUrl,
                            Signal, Slot)
from PySide6.QtGui import QImage, QOpenGLContext
from PySide6.QtOpenGL import QOpenGLFramebufferObject, QOpenGLFramebufferObjectFormat
from PySide6.QtQuick import QQuickFramebufferObject, QQuickImageProvider, QQuickOpenGLUtils

import mpv

import shutil
import sys

# yt-dlp (+ the QuickJS runtime it uses for YouTube) ships in app\tools on Windows; elsewhere use PATH.
TOOLS = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "tools")
_EXE = ".exe" if sys.platform == "win32" else ""
YTDLP = next((p for p in (os.path.join(TOOLS, "yt-dlp" + _EXE), shutil.which("yt-dlp")) if p and os.path.exists(p)), "")
QJS = next((p for p in (os.path.join(TOOLS, "qjs" + _EXE), shutil.which("qjs")) if p and os.path.exists(p)), "")


def _ytdl_options():
    """mpv's ytdl hook reads these when it loads, so they go in at creation time."""
    if not YTDLP:
        return {"ytdl": "no"}
    o = {"ytdl": "yes", "script_opts": "ytdl_hook-ytdl_path=" + YTDLP}
    if QJS:
        o["ytdl_raw_options"] = "js-runtimes=quickjs:" + QJS
    return o

# ---------------------------------------------------------------- player ---

# (mpv property, qml name, qt type, default)
OBSERVED = [
    ("time-pos", "position", float, 0.0),
    ("duration", "duration", float, 0.0),
    ("pause", "paused", bool, True),
    ("volume", "volume", float, 100.0),
    ("volume-max", "volumeMax", float, 130.0),
    ("mute", "muted", bool, False),
    ("speed", "speed", float, 1.0),
    ("sub-delay", "subDelay", float, 0.0),
    ("secondary-sub-delay", "secondarySubDelay", float, 0.0),
    ("audio-delay", "audioDelay", float, 0.0),
    ("sub-scale", "subScale", float, 1.0),
    ("sub-pos", "subPos", float, 100.0),
    ("sub-visibility", "subVisible", bool, True),
    ("sid", "sid", "QVariant", False),
    ("secondary-sid", "secondarySid", "QVariant", False),
    ("aid", "aid", "QVariant", False),
    ("vid", "vid", "QVariant", False),
    ("track-list", "tracks", "QVariantList", []),
    ("chapter-list", "chapters", "QVariantList", []),
    ("chapter", "chapter", "QVariant", None),
    ("playlist", "playlist", "QVariantList", []),
    ("playlist-pos", "playlistPos", int, -1),
    ("media-title", "mediaTitle", str, ""),
    ("path", "path", str, ""),
    ("filename", "fileName", str, ""),
    ("idle-active", "idle", bool, True),
    ("eof-reached", "eofReached", bool, False),
    ("loop-file", "loopFile", "QVariant", False),
    ("loop-playlist", "loopPlaylist", "QVariant", False),
    ("ab-loop-a", "abLoopA", "QVariant", "no"),
    ("ab-loop-b", "abLoopB", "QVariant", "no"),
    ("demuxer-cache-time", "cacheTime", float, 0.0),
    ("paused-for-cache", "buffering", bool, False),
    ("seeking", "seeking", bool, False),
    ("video-params", "videoParams", "QVariantMap", {}),
    ("audio-params", "audioParams", "QVariantMap", {}),
    ("video-codec", "videoCodec", str, ""),
    ("audio-codec-name", "audioCodec", str, ""),
    ("hwdec-current", "hwdecCurrent", str, ""),
    ("hwdec", "hwdec", "QVariant", "auto-safe"),
    ("container-fps", "fps", float, 0.0),
    ("video-bitrate", "videoBitrate", float, 0.0),
    ("audio-bitrate", "audioBitrate", float, 0.0),
    ("file-size", "fileSize", float, 0.0),
    ("file-format", "fileFormat", str, ""),
    ("frame-drop-count", "frameDrops", int, 0),
    ("brightness", "brightness", int, 0),
    ("contrast", "contrast", int, 0),
    ("saturation", "saturation", int, 0),
    ("gamma", "gamma", int, 0),
    ("hue", "hue", int, 0),
    ("video-aspect-override", "aspectOverride", "QVariant", "-1"),
    ("video-rotate", "rotate", int, 0),
    ("video-zoom", "zoom", float, 0.0),
    ("video-pan-x", "panX", float, 0.0),
    ("video-pan-y", "panY", float, 0.0),
    ("deinterlace", "deinterlace", "QVariant", False),
    ("deband", "deband", bool, False),
    ("vf", "vf", "QVariantList", []),
    ("af", "af", "QVariantList", []),
    ("audio-device-list", "audioDevices", "QVariantList", []),
    ("audio-device", "audioDevice", str, "auto"),
]

_CASTS = {float: float, int: int, bool: bool, str: str}


def _clean(value):
    """Make mpv node values safe for QVariant conversion (bytes -> str, nested)."""
    if isinstance(value, bytes):
        return value.decode("utf-8", "replace")
    if isinstance(value, dict):
        return {(k.decode() if isinstance(k, bytes) else k): _clean(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)):
        return [_clean(v) for v in value]
    return value


def _build_player_class():
    ns = {}
    for prop, qname, qtype, default in OBSERVED:
        sig = Signal()
        ns[qname + "Changed"] = sig

        def getter(self, _q=qname):
            return self._state[_q]

        ns[qname] = Property(qtype, getter, notify=sig)
    return ns


class Player(QObject):
    """Owns the libmpv handle; mirrors observed properties as Qt properties."""

    for _k, _v in _build_player_class().items():
        locals()[_k] = _v
    del _k, _v

    _propArrived = Signal(str, "QVariant")
    _eventArrived = Signal(str, "QVariant")
    fileLoaded = Signal()
    endFile = Signal(str)
    logMessage = Signal(str)

    def __init__(self, config_dir, parent=None):
        super().__init__(parent)
        self._state = {q: d for _, q, _, d in OBSERVED}
        self._types = {q: t for _, q, t, _ in OBSERVED}
        self._byprop = {p: q for p, q, _, _ in OBSERVED}
        self.config_dir = config_dir
        os.makedirs(os.path.join(config_dir, "watch_later"), exist_ok=True)
        shots = os.path.join(os.path.expanduser("~"), "Pictures", "Lumen Screenshots")

        self.mpv = mpv.MPV(
            vo="libmpv",
            idle="yes",
            keep_open="yes",
            osc="no",
            input_default_bindings="no",
            input_vo_keyboard="no",
            osd_level="0",
            hwdec="auto-safe",
            save_position_on_quit="yes",
            watch_later_directory=os.path.join(config_dir, "watch_later"),
            screenshot_directory=shots,
            screenshot_format="png",
            screenshot_template="%F-%wH.%wM.%wS",
            sub_auto="fuzzy",
            audio_file_auto="fuzzy",
            volume_max="200",
            reset_on_next_file="sub-delay,audio-delay,ab-loop-a,ab-loop-b,video-zoom,"
                               "video-pan-x,video-pan-y,video-rotate,secondary-sid",
            log_handler=self._on_log,
            loglevel="warn",
            **_ytdl_options(),
        )
        self.configure_streaming(1080)
        self._propArrived.connect(self._apply_prop, Qt.QueuedConnection)
        self._eventArrived.connect(self._apply_event, Qt.QueuedConnection)
        for prop, qname, _, _ in OBSERVED:
            try:
                self.mpv.observe_property(prop, self._make_observer(prop))
            except Exception:
                pass
        self.mpv.register_event_callback(self._on_event)

    @Slot(int)
    def configure_streaming(self, max_height):
        """Web pages (YouTube, Vimeo, Twitch, …) are resolved by yt-dlp through mpv's ytdl hook."""
        try:
            cap = f"[height<=?{max_height}]" if max_height > 0 else ""
            self.mpv["ytdl-format"] = f"bestvideo{cap}+bestaudio/best{cap}/best"
        except Exception as e:
            print("streaming setup:", e, flush=True)

    streamingAvailable = Property(bool, lambda s: bool(YTDLP), constant=True)

    # mpv thread -> Qt thread
    def _make_observer(self, prop):
        def observer(_name, value):
            self._propArrived.emit(prop, _clean(value))
        return observer

    def _on_event(self, event):
        try:
            eid = event.event_id.value
        except Exception:
            return
        if eid == mpv.MpvEventID.FILE_LOADED:
            self._eventArrived.emit("file-loaded", None)
        elif eid == mpv.MpvEventID.END_FILE:
            try:
                reason = str(event.data.reason)
            except Exception:
                reason = ""
            self._eventArrived.emit("end-file", reason)

    def _on_log(self, level, prefix, text):
        self.logMessage.emit(f"[{level}] {prefix}: {text.strip()}")

    @Slot(str, "QVariant")
    def _apply_prop(self, prop, value):
        qname = self._byprop[prop]
        t = self._types[qname]
        if t in _CASTS:
            if value is None:
                value = next(d for _, q, _, d in OBSERVED if q == qname)
            else:
                try:
                    value = _CASTS[t](value)
                except (TypeError, ValueError):
                    return
        elif value is None and t in ("QVariantList", "QVariantMap"):
            value = [] if t == "QVariantList" else {}
        if self._state[qname] == value and t not in ("QVariantList", "QVariantMap"):
            return
        self._state[qname] = value
        getattr(self, qname + "Changed").emit()

    @Slot(str, "QVariant")
    def _apply_event(self, name, data):
        if name == "file-loaded":
            self.fileLoaded.emit()
        elif name == "end-file":
            self.endFile.emit(data or "")

    # ---- QML API
    @Slot("QVariantList")
    def command(self, args):
        try:
            self.mpv.command(*[str(int(a)) if isinstance(a, float) and a.is_integer() else str(a)
                               for a in args])
        except Exception as e:
            self.logMessage.emit(f"command {args} failed: {e}")

    @Slot(str, "QVariant")
    def setProp(self, name, value):
        try:
            if isinstance(value, bool):
                value = "yes" if value else "no"
            elif isinstance(value, float) and value.is_integer():
                value = int(value)   # QML numbers arrive as floats; int options reject "82.0"
            self.mpv.set_property(name, value)
        except Exception as e:
            try:
                self.mpv.command("set", name, str(value))
            except Exception:
                self.logMessage.emit(f"set {name}={value} failed: {e}")

    @Slot(str, result="QVariant")
    def getProp(self, name):
        try:
            return _clean(self.mpv._get_property(name))
        except Exception:
            return None

    @Slot(str, float)
    def addProp(self, name, delta):
        self.command(["add", name, delta])

    @Slot(str)
    def cycle(self, name):
        self.command(["cycle", name])

    @Slot(str)
    @Slot(str, str)
    def open(self, target, mode="replace"):
        path = _to_path(target)
        if not path:
            return
        try:
            self.mpv.command("write-watch-later-config")
        except Exception:
            pass
        self.command(["loadfile", path, mode])

    @Slot(str)
    def addSubtitle(self, target):
        self.command(["sub-add", _to_path(target), "select"])

    @Slot(str)
    def addAudio(self, target):
        self.command(["audio-add", _to_path(target), "select"])

    @Slot(float)
    def seekTo(self, seconds):
        self.command(["seek", max(0.0, seconds), "absolute+exact"])

    @Slot(float)
    def seekBy(self, seconds):
        self.command(["seek", seconds, "relative+exact"])

    @Slot(str, result="QVariant")
    def expand(self, text):
        try:
            return self.mpv.expand_text(text)
        except Exception:
            return ""

    @Slot(str, str)
    def toggleFilter(self, kind, spec):
        """kind is 'vf' or 'af'; spec like '@deband:gradfun' ; toggles by label."""
        self.command([kind, "toggle", spec])

    @Slot(result=bool)
    def hasVideo(self):
        return bool(self._state["vid"]) and bool(self._state["videoParams"])

    @Slot()
    def saveState(self):
        try:
            if not self._state["idle"]:
                self.mpv.command("write-watch-later-config")
        except Exception:
            pass

    def shutdown(self):
        # Only persist state: the render context lives on Qt's render thread, and
        # mpv_terminate_destroy aborts while it exists. The process exits right after.
        self.saveState()


def _to_path(target):
    if not target:
        return ""
    if target.startswith("file:"):
        return QUrl(target).toLocalFile()
    return target


# ----------------------------------------------------------- video item ---

def _get_proc_address(_ctx, name):
    ctx = QOpenGLContext.currentContext()
    if ctx is None:
        return 0
    addr = ctx.getProcAddress(name.decode() if isinstance(name, bytes) else name)
    return int(addr) if addr else 0


_proc_fn = mpv.MpvGlGetProcAddressFn(_get_proc_address)


_KEEP_ALIVE = []


class _VideoRenderer(QQuickFramebufferObject.Renderer):
    def __init__(self, item):
        super().__init__()
        self._item = item
        self._ctx = None
        self._fbo_info = None

    def createFramebufferObject(self, size):
        if self._ctx is None:
            player = self._item.player
            self._ctx = mpv.MpvRenderContext(
                player.mpv, "opengl",
                opengl_init_params={"get_proc_address": _proc_fn})
            self._ctx.update_cb = self._item.requestFrame.emit
            self._item._ctx = self._ctx
            self._item.contextCreated.emit()
        fmt = QOpenGLFramebufferObjectFormat()
        fmt.setAttachment(QOpenGLFramebufferObject.CombinedDepthStencil)
        # Qt takes ownership of the returned FBO and deletes it on resize/teardown, but
        # PySide would also delete it once the wrapper is collected. Keep every wrapper
        # alive for the life of the process (main() exits with os._exit).
        fbo = QOpenGLFramebufferObject(size, fmt)
        _KEEP_ALIVE.append(fbo)
        self._fbo_info = {"w": size.width(), "h": size.height(), "fbo": fbo.handle()}
        return fbo

    def render(self):
        if self._ctx is not None and self._fbo_info:
            self._ctx.render(flip_y=False, opengl_fbo=self._fbo_info)
        QQuickOpenGLUtils.resetOpenGLState()



class VideoItem(QQuickFramebufferObject):
    requestFrame = Signal()
    playerChanged = Signal()
    contextCreated = Signal()
    readyChanged = Signal()
    default_player = None

    def __init__(self, parent=None):
        super().__init__(parent)
        self._player = VideoItem.default_player
        self._ctx = None
        self.setMirrorVertically(False)
        self._ready = False
        self.requestFrame.connect(self.update, Qt.QueuedConnection)
        self.contextCreated.connect(self._mark_ready, Qt.QueuedConnection)

    def _mark_ready(self):
        self._ready = True
        self.readyChanged.emit()

    ready = Property(bool, lambda self: self._ready, notify=readyChanged)

    def getPlayer(self):
        return self._player

    def setPlayer(self, p):
        self._player = p
        self.playerChanged.emit()

    player = Property(QObject, getPlayer, setPlayer, notify=playerChanged)

    def createRenderer(self):
        return _VideoRenderer(self)


# ---------------------------------------------------------- thumbnails ---

_SW_SIZE, _SW_FORMAT, _SW_STRIDE, _SW_POINTER = 17, 18, 19, 20


class Thumbnailer(QObject):
    """A second, silent libmpv instance using the software renderer for seek previews."""
    ready = Signal(str)   # image id

    W, H = 320, 180

    def __init__(self, provider, parent=None):
        super().__init__(parent)
        self.provider = provider
        self._lock = threading.Lock()
        self._want = None          # (path, t)
        self._wake = threading.Event()
        self._restart = threading.Event()
        self._path = None
        self._stop = False
        self._counter = 0
        self.enabled = True
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    @Slot(str, float)
    def request(self, path, t):
        if not self.enabled or not path or "://" in path and not path.startswith("file:"):
            return
        with self._lock:
            self._want = (_to_path(path), round(t, 1))
        self._wake.set()

    def stop(self):
        self._stop = True
        self._wake.set()

    def _run(self):
        try:
            m = mpv.MPV(vo="libmpv", idle="yes", pause="yes", aid="no", sid="no", audio="no",
                        hwdec="no", hr_seek="no", osd_level="0", load_scripts="no",
                        ytdl="no", sub_auto="no", audio_file_auto="no", resume_playback="no",
                        cache="no", demuxer_readahead_secs="0", vd_lavc_skiploopfilter="all",
                        vd_lavc_fast="yes", vd_lavc_threads="2", keep_open="always",
                        terminal="no", input_default_bindings="no")
        except Exception:
            return
        ctx = mpv.MpvRenderContext(m, "sw")
        frame_ready = threading.Event()
        ctx.update_cb = frame_ready.set

        @m.event_callback("playback-restart")
        def _restart(_e):
            self._restart.set()

        buf = (ctypes.c_ubyte * (self.W * self.H * 4))()
        size = (ctypes.c_int * 2)(self.W, self.H)
        fmt = ctypes.c_char_p(b"bgr0")
        stride = ctypes.c_size_t(self.W * 4)

        def param(type_id, ptr):
            p = mpv.MpvRenderParam("invalid")
            p.type_id = type_id
            p.data = ctypes.cast(ptr, ctypes.c_void_p)
            return p

        params = (mpv.MpvRenderParam * 5)(
            param(_SW_SIZE, ctypes.pointer(size)),
            param(_SW_FORMAT, fmt),
            param(_SW_STRIDE, ctypes.pointer(stride)),
            param(_SW_POINTER, buf),
            mpv.MpvRenderParam("invalid"))

        while not self._stop:
            self._wake.wait()
            self._wake.clear()
            if self._stop:
                break
            with self._lock:
                want, self._want = self._want, None
            if not want:
                continue
            path, t = want
            try:
                self._restart.clear()
                if path != self._path:
                    m.command("loadfile", path, "replace")
                    self._path = path
                    # wait for the file to open before seeking
                    deadline = time.time() + 4
                    while time.time() < deadline and m._get_property("duration") is None:
                        time.sleep(0.02)
                    self._restart.clear()
                m.command("seek", str(t), "absolute+keyframes")
                if not self._restart.wait(2.0):
                    continue
                frame_ready.wait(0.5)
                frame_ready.clear()
                ctx.update()
                mpv._mpv_render_context_render(ctx.handle, params)
                img = QImage(bytes(buf), self.W, self.H, self.W * 4, QImage.Format_RGB32).copy()
                self._counter += 1
                key = f"{self._counter}"
                self.provider.put(key, img)
                self.ready.emit(key)
            except Exception:
                self._path = None
                continue
        try:
            ctx.free()
            m.terminate()
        except Exception:
            pass


class ThumbProvider(QQuickImageProvider):
    def __init__(self):
        super().__init__(QQuickImageProvider.Image)
        self._images = {}
        self._lock = threading.Lock()

    def put(self, key, img):
        with self._lock:
            self._images[key] = img
            # keep the last few only
            if len(self._images) > 8:
                for k in list(self._images)[:-8]:
                    del self._images[k]

    def requestImage(self, ident, size, requested):
        with self._lock:
            img = self._images.get(ident.split("?")[0])
        if img is None:
            img = QImage(Thumbnailer.W, Thumbnailer.H, QImage.Format_RGB32)
            img.fill(0)
        return img
