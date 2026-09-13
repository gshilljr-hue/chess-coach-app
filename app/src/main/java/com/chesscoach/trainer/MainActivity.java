package com.chesscoach.trainer;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * The whole app: one WebView showing app/src/main/assets/index.html, which is the
 * complete trainer — board, chess engine, opening book and analysis all in that
 * single file. Nothing is fetched at runtime, so the app works with no signal.
 */
public class MainActivity extends Activity {

    /** ?native=1 tells the page it is running inside this wrapper rather than in a
     *  browser tab, so it hides its "add to home screen" bar. */
    private static final String PAGE = "file:///android_asset/index.html?native=1";

    private WebView web;

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
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(PAGE);
        }
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
