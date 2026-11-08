package io.github.tslegend711.lydia;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.Window;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Ponte tra il codice web e Android: dati dei widget e colore delle barre di sistema. */
@CapacitorPlugin(name = "LydiaWidget")
public class LydiaWidgetPlugin extends Plugin {
    static volatile int topInsetPx = 0;
    static volatile int bottomInsetPx = 0;

    /** Altezza della barra di stato in pixel CSS, per far continuare la barra dell'app sotto l'ora. */
    @PluginMethod
    public void getInsets(PluginCall call) {
        float d = getContext().getResources().getDisplayMetrics().density;
        com.getcapacitor.JSObject r = new com.getcapacitor.JSObject();
        r.put("top", d > 0 ? topInsetPx / d : 0);
        r.put("bottom", d > 0 ? bottomInsetPx / d : 0);
        call.resolve(r);
    }

    @PluginMethod
    public void setData(PluginCall call) {
        String data = call.getString("data", "");
        Context c = getContext();
        c.getSharedPreferences(LydiaWidgetProvider.PREFS, Context.MODE_PRIVATE)
                .edit().putString("data", data).apply();
        LydiaWidgetProvider.updateAll(c);
        call.resolve();
    }

    /** Colora barra di stato e barra di navigazione come lo sfondo dell'app, con icone chiare o scure. */
    @PluginMethod
    public void setBars(PluginCall call) {
        final String col = call.getString("color", "#0B1020");
        final boolean light = Boolean.TRUE.equals(call.getBoolean("light", false));
        final Activity a = getActivity();
        if (a == null) {
            call.resolve();
            return;
        }
        a.runOnUiThread(() -> {
            try {
                int c = Color.parseColor(col);
                Window w = a.getWindow();
                w.getDecorView().setBackgroundColor(c);
                w.setStatusBarColor(Color.TRANSPARENT);
                w.setNavigationBarColor(Color.TRANSPARENT);
                if (android.os.Build.VERSION.SDK_INT >= 29) w.setNavigationBarContrastEnforced(false);
                WindowInsetsControllerCompat ic = WindowCompat.getInsetsController(w, w.getDecorView());
                ic.setAppearanceLightStatusBars(light);
                ic.setAppearanceLightNavigationBars(light);
            } catch (Exception ignored) { }
            call.resolve();
        });
    }

    /** C'è un'impronta (o il volto) registrata sul telefono? */
    @PluginMethod
    public void bioAvailable(PluginCall call) {
        com.getcapacitor.JSObject r = new com.getcapacitor.JSObject();
        boolean ok = false;
        try {
            ok = BiometricManager.from(getContext()).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Exception ignored) { }
        r.put("ok", ok);
        call.resolve(r);
    }

    /** Chiede l'impronta per sbloccare Lydia. */
    @PluginMethod
    public void biometric(final PluginCall call) {
        final Activity a = getActivity();
        if (!(a instanceof FragmentActivity)) {
            com.getcapacitor.JSObject r = new com.getcapacitor.JSObject();
            r.put("ok", false);
            call.resolve(r);
            return;
        }
        final String title = call.getString("title", "Sblocca Lydia");
        final String sub = call.getString("subtitle", "");
        a.runOnUiThread(() -> {
            try {
                BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title).setSubtitle(sub).setNegativeButtonText("Usa il PIN")
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).build();
                BiometricPrompt p = new BiometricPrompt((FragmentActivity) a, ContextCompat.getMainExecutor(a), new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        com.getcapacitor.JSObject r = new com.getcapacitor.JSObject();
                        r.put("ok", true);
                        call.resolve(r);
                    }

                    @Override
                    public void onAuthenticationError(int code, CharSequence msg) {
                        com.getcapacitor.JSObject r = new com.getcapacitor.JSObject();
                        r.put("ok", false);
                        call.resolve(r);
                    }
                });
                p.authenticate(info);
            } catch (Exception e) {
                com.getcapacitor.JSObject r = new com.getcapacitor.JSObject();
                r.put("ok", false);
                call.resolve(r);
            }
        });
    }
}
