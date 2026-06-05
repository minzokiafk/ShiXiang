from yt_dlp.extractor.youtube.jsc._builtin.ejs import EJSBaseJCP
from yt_dlp.extractor.youtube.jsc.provider import (
    JsChallengeProvider,
    register_preference,
    register_provider,
)
from yt_dlp.utils._jsruntime import JsRuntimeInfo


try:
    from java import jclass

    _QuickJsBridge = jclass("com.pixelpoint.mediadownloader.QuickJsBridge")
except Exception:
    _QuickJsBridge = None


@register_provider
class ChaquopyQuickJSJCP(EJSBaseJCP):
    PROVIDER_NAME = "chaquopy_quickjs"
    JS_RUNTIME_NAME = "chaquopy_quickjs"

    @property
    def runtime_info(self):
        if _QuickJsBridge is None:
            return None
        return JsRuntimeInfo(
            name="chaquopy-quickjs",
            path="android-embedded",
            version="0.9.2",
            version_tuple=(0, 9, 2),
            supported=True,
        )

    def _run_js_runtime(self, stdin: str, /) -> str:
        if _QuickJsBridge is None:
            raise RuntimeError("Android QuickJS bridge is not available")
        return _QuickJsBridge.evaluateForYtDlp(stdin)


@register_preference(ChaquopyQuickJSJCP)
def preference(provider: JsChallengeProvider, requests) -> int:
    return 950
