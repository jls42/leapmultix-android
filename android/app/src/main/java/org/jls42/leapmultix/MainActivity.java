package org.jls42.leapmultix;

import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.activity.OnBackPressedCallback;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebChromeClient;
import com.getcapacitor.WebViewListener;

/**
 * Au lancement, après l'écran du système (le panda), l'image de bienvenue en portrait couvre
 * le jeu pendant qu'il se charge, puis s'efface en fondu : au moins MIN_MS à l'écran, retirée
 * dès que la page est chargée, et au plus tard après MAX_MS.
 *
 * Plein écran des jeux d'Arcade : le jeu le demande à la page (API Fullscreen), et la WebView
 * le transmet à l'appli (onShowCustomView). Le client de Capacitor le refusait aussitôt ;
 * celui-ci pose la vue plein écran par-dessus tout, la retire à la sortie, et le bouton retour
 * quitte d'abord le plein écran.
 */
public class MainActivity extends BridgeActivity {

    private static final long MIN_MS = 1200;
    private static final long MAX_MS = 6000;
    private static final long FADE_MS = 350;
    // Bleu nuit des bords de l'image : sur un écran plus allongé qu'elle, les bandes s'y fondent
    private static final int SPACE_COLOR = Color.rgb(0x00, 0x06, 0x1a);

    private ImageView welcome;
    private long shownAt;
    private FullscreenChromeClient chromeClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Avant super.onCreate : le pont de Capacitor annonce ses plugins à la page en démarrant
        registerPlugin(FilesPlugin.class);
        super.onCreate(savedInstanceState);
        if (getBridge() != null) {
            chromeClient = new FullscreenChromeClient(getBridge());
            getBridge().getWebView().setWebChromeClient(chromeClient);
            getOnBackPressedDispatcher()
                .addCallback(
                    this,
                    new OnBackPressedCallback(true) {
                        @Override
                        public void handleOnBackPressed() {
                            if (chromeClient.exitFullscreen()) return;
                            // Hors plein écran : le comportement habituel du bouton retour
                            setEnabled(false);
                            getOnBackPressedDispatcher().onBackPressed();
                            setEnabled(true);
                        }
                    }
                );
        }
        // Une activité recréée (rotation, retour d'arrière-plan) garde son jeu affiché
        if (savedInstanceState == null && getBridge() != null) {
            showWelcome();
        }
    }

    private void showWelcome() {
        welcome = new ImageView(this);
        welcome.setImageResource(R.drawable.bienvenue);
        welcome.setScaleType(ImageView.ScaleType.FIT_CENTER);
        welcome.setBackgroundColor(SPACE_COLOR);
        welcome.setContentDescription(getString(R.string.app_name));
        // Les touchers pendant l'image ne tombent pas sur le jeu caché dessous
        welcome.setClickable(true);
        addContentView(
            welcome,
            new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        );
        shownAt = SystemClock.uptimeMillis();
        getBridge()
            .addWebViewListener(
                new WebViewListener() {
                    @Override
                    public void onPageLoaded(WebView webView) {
                        hideWelcome();
                    }
                }
            );
        welcome.postDelayed(this::hideWelcome, MAX_MS);
    }

    /** Client de Capacitor (permissions, choix de fichiers…) qui accepte le plein écran */
    private final class FullscreenChromeClient extends BridgeWebChromeClient {

        private View fullscreenView;
        private CustomViewCallback fullscreenCallback;

        FullscreenChromeClient(Bridge bridge) {
            super(bridge);
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (fullscreenView != null) {
                callback.onCustomViewHidden();
                return;
            }
            fullscreenView = view;
            fullscreenCallback = callback;
            ((ViewGroup) getWindow().getDecorView()).addView(
                    view,
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                );
        }

        @Override
        public void onHideCustomView() {
            if (fullscreenView == null) return;
            ((ViewGroup) getWindow().getDecorView()).removeView(fullscreenView);
            fullscreenView = null;
            CustomViewCallback callback = fullscreenCallback;
            fullscreenCallback = null;
            if (callback != null) callback.onCustomViewHidden();
        }

        /** Quitte le plein écran s'il est en cours ; rend false sinon */
        boolean exitFullscreen() {
            if (fullscreenView == null) return false;
            onHideCustomView();
            return true;
        }
    }

    private void hideWelcome() {
        final ImageView image = welcome;
        if (image == null) return;
        welcome = null;
        long wait = Math.max(0, MIN_MS - (SystemClock.uptimeMillis() - shownAt));
        image.postDelayed(
            () ->
                image
                    .animate()
                    .alpha(0f)
                    .setDuration(FADE_MS)
                    .withEndAction(() -> {
                        ViewGroup parent = (ViewGroup) image.getParent();
                        if (parent != null) parent.removeView(image);
                        image.setImageDrawable(null);
                        image.setVisibility(View.GONE);
                    }),
            wait
        );
    }
}
