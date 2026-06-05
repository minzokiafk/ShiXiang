package com.pixelpoint.mediadownloader;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;

public final class QuickJsBridge {
    private static final long JS_TIMEOUT_SECONDS = 45;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static volatile Context appContext;
    private static volatile WebView webView;

    private static final String YT_DLP_POLYFILLS =
        "if (typeof globalThis === 'undefined') { var globalThis = this; }\n"
            + "if (!String.prototype.replaceAll) {\n"
            + "  String.prototype.replaceAll = function(search, replacement) {\n"
            + "    return String(this).split(search).join(replacement);\n"
            + "  };\n"
            + "}\n"
            + "if (!Object.hasOwn) {\n"
            + "  Object.hasOwn = function(object, property) {\n"
            + "    return Object.prototype.hasOwnProperty.call(Object(object), property);\n"
            + "  };\n"
            + "}\n";

    private QuickJsBridge() {
    }

    public static void initialize(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    public static String evaluateForYtDlp(String source) {
        if (appContext == null) {
            throw new RuntimeException("WebView JS bridge is not initialized");
        }
        return evaluateInWebView(source);
    }

    private static String evaluateInWebView(String source) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> output = new AtomicReference<>("");
        AtomicReference<Throwable> error = new AtomicReference<>();

        mainHandler.post(() -> {
            try {
                WebView view = webView;
                if (view == null) {
                    view = new WebView(appContext);
                    view.getSettings().setJavaScriptEnabled(true);
                    webView = view;
                }
                view.evaluateJavascript(webViewScript(source), encoded -> {
                    try {
                        String payload = decodeEvaluateJavascriptString(encoded);
                        JSONObject json = new JSONObject(payload);
                        if (!json.optBoolean("ok", false)) {
                            error.set(new RuntimeException(json.optString("error", "WebView JS execution failed")));
                        } else {
                            output.set(json.optString("value", ""));
                        }
                    } catch (Throwable throwable) {
                        error.set(throwable);
                    } finally {
                        latch.countDown();
                    }
                });
            } catch (Throwable throwable) {
                error.set(throwable);
                latch.countDown();
            }
        });

        try {
            if (!latch.await(JS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new RuntimeException("WebView JS execution timed out after " + JS_TIMEOUT_SECONDS + " seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("WebView JS execution interrupted", e);
        }

        Throwable throwable = error.get();
        if (throwable != null) {
            throw new RuntimeException("WebView JS execution failed: " + throwable.getMessage(), throwable);
        }
        return output.get();
    }

    private static String webViewScript(String source) {
        return "(function() {\n"
            + "  try {\n"
            + YT_DLP_POLYFILLS
            + "    globalThis.__ytDlpOutput = '';\n"
            + "    globalThis.console = { log: function(value) { globalThis.__ytDlpOutput = String(value); } };\n"
            + source
            + "\n    return JSON.stringify({ ok: true, value: String(globalThis.__ytDlpOutput || '') });\n"
            + "  } catch (e) {\n"
            + "    return JSON.stringify({ ok: false, error: String((e && (e.stack || e.message)) || e) });\n"
            + "  }\n"
            + "})()";
    }

    private static String decodeEvaluateJavascriptString(String encoded) throws Exception {
        if (encoded == null || "null".equals(encoded)) {
            return "{}";
        }
        return new JSONArray("[" + encoded + "]").optString(0, "{}");
    }

}
