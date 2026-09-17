package com.chesscoach.trainer;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * The whole app: one WebView showing app/src/main/assets/index.html, which is the
 * complete trainer — board, chess engine, opening book and analysis all in that
 * single file. Nothing is fetched at runtime, so the app works with no signal.
 *
 * <p>Two things here exist only so the page can be given a picture of a board:
 * a file chooser, without which an {@code <input type="file">} in a WebView does
 * nothing at all, and a share target, so a screenshot taken anywhere on the
 * phone can be sent straight in.
 */
public class MainActivity extends Activity {

    /** ?native=1 tells the page it is running inside this wrapper rather than in a
     *  browser tab, so it hides its "add to home screen" bar. */
    private static final String PAGE = "file:///android_asset/index.html?native=1";

    private static final int REQ_FILE = 1;

    /** Pictures are handed to the page as a data URL. Full-size phone screenshots
     *  would make that string enormous for no benefit — the recogniser works at
     *  1200px — so they are scaled down on the way through. */
    private static final int MAX_EDGE = 1300;

    private WebView web;
    private ValueCallback<Uri[]> pendingFileCallback;
    private String pendingImage;
    private boolean pageReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        setContentView(web);

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        // The board sizes itself to the viewport, so browser zoom would only ever
        // fight the layout.
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // Honour the page's own type scale rather than the system font-size slider,
        // which would otherwise push the board and the analysis panel out of shape.
        settings.setTextZoom(100);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                applySystemTheme();
                pageReady = true;
                flushPendingImage();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (pendingFileCallback != null) {
                    pendingFileCallback.onReceiveValue(null);
                }
                pendingFileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    pendingFileCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
                return true;
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(PAGE);
        }
        handleShare(getIntent());
    }

    /** A screenshot shared into the app while it was already running. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShare(intent);
    }

    private void handleShare(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        String type = intent.getType();
        if (type == null || !type.startsWith("image/")) return;
        Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri == null) return;
        String dataUrl = readAsDataUrl(uri);
        if (dataUrl == null) return;
        pendingImage = dataUrl;
        flushPendingImage();
    }

    /** The page may not have finished loading when a share arrives, so the
     *  picture waits until it has. */
    private void flushPendingImage() {
        if (!pageReady || pendingImage == null || web == null) return;
        String js = "window.receiveSharedImage && window.receiveSharedImage('" + pendingImage + "');";
        pendingImage = null;
        web.evaluateJavascript(js, null);
    }

    /**
     * Decode a shared or picked image, shrink it if it is larger than the
     * recogniser can use, and encode it as a PNG data URL. PNG rather than JPEG
     * because the recogniser thresholds colours against the square underneath,
     * and JPEG ringing around the edge of a piece is exactly the kind of noise
     * that would blur.
     */
    private String readAsDataUrl(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            InputStream probe = getContentResolver().openInputStream(uri);
            if (probe == null) return null;
            BitmapFactory.decodeStream(probe, null, bounds);
            probe.close();

            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 1;
            while (longest / opts.inSampleSize > MAX_EDGE * 2) {
                opts.inSampleSize *= 2;
            }

            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
            in.close();
            if (bmp == null) return null;

            int w = bmp.getWidth(), h = bmp.getHeight();
            int edge = Math.max(w, h);
            if (edge > MAX_EDGE) {
                float k = (float) MAX_EDGE / edge;
                Bitmap scaled = Bitmap.createScaledBitmap(
                        bmp, Math.round(w * k), Math.round(h * k), true);
                if (scaled != bmp) bmp.recycle();
                bmp = scaled;
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);

            /* The data URL is handed over by evaluating a string of JavaScript,
             * and a very large string is exactly where that becomes unreliable
             * across devices. A busy screenshot can still make a big PNG even
             * after scaling, so past a sane size fall back to a high-quality
             * JPEG. The recogniser measures its threshold from each square's own
             * corners, so mild JPEG noise costs it very little — whereas a
             * picture that never arrives costs everything. */
            String mime = "image/png";
            if (out.size() > 1200000) {
                ByteArrayOutputStream jpg = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, jpg);
                out = jpg;
                mime = "image/jpeg";
            }
            bmp.recycle();
            return "data:" + mime + ";base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_FILE) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (pendingFileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            result = new Uri[]{data.getData()};
        }
        pendingFileCallback.onReceiveValue(result);
        pendingFileCallback = null;
    }

    /**
     * Push the system light/dark setting into the page.
     * <p>
     * The page already reacts to {@code prefers-color-scheme}, but a WebView does not
     * reliably report the host app's night mode to CSS. It also supports an explicit
     * {@code data-theme} override on the root element, which is unambiguous — so the
     * wrapper drives that directly and dark mode follows the phone every time.
     */
    private void applySystemTheme() {
        int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        String theme = (night == Configuration.UI_MODE_NIGHT_YES) ? "dark" : "light";
        web.evaluateJavascript(
                "document.documentElement.setAttribute('data-theme','" + theme + "');", null);
    }

    /** Declared in the manifest's configChanges, so switching to dark mode or rotating
     *  the phone re-themes in place instead of restarting the game. */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applySystemTheme();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }
}
