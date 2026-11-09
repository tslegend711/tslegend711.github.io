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

    /** Legge il testo di una foto (busta paga o estratto conto) sul telefono, senza internet. */
    @PluginMethod
    public void ocr(final PluginCall call) {
        String b64 = call.getString("data", "");
        try {
            byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp == null) {
                call.reject("Immagine non leggibile");
                return;
            }
            final int w = bmp.getWidth(), h = bmp.getHeight();
            com.google.mlkit.vision.common.InputImage img = com.google.mlkit.vision.common.InputImage.fromBitmap(bmp, 0);
            com.google.mlkit.vision.text.TextRecognizer rec = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS);
            rec.process(img).addOnSuccessListener(text -> {
                try {
                    org.json.JSONArray words = new org.json.JSONArray();
                    for (com.google.mlkit.vision.text.Text.TextBlock b : text.getTextBlocks()) {
                        for (com.google.mlkit.vision.text.Text.Line l : b.getLines()) {
                            for (com.google.mlkit.vision.text.Text.Element e : l.getElements()) {
                                android.graphics.Rect r = e.getBoundingBox();
                                if (r == null) continue;
                                org.json.JSONObject o = new org.json.JSONObject();
                                o.put("t", e.getText());
                                o.put("x", r.left);
                                o.put("y", r.top);
                                o.put("w", r.width());
                                o.put("h", r.height());
                                words.put(o);
                            }
                        }
                    }
                    com.getcapacitor.JSObject res = new com.getcapacitor.JSObject();
                    res.put("w", w);
                    res.put("h", h);
                    res.put("words", words.toString());
                    call.resolve(res);
                } catch (Exception ex) {
                    call.reject("Errore nella lettura della foto");
                }
            }).addOnFailureListener(ex -> call.reject("Non riesco a leggere il testo della foto"));
        } catch (Exception e) {
            call.reject("Immagine non leggibile");
        }
    }

    /** PDF senza testo (scansioni o testo disegnato): disegno le pagine e leggo il testo come da una foto. */
    @PluginMethod
    public void ocrPdf(final PluginCall call) {
        final String b64 = call.getString("data", "");
        new Thread(() -> {
            java.io.File tmp = null;
            try {
                byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                tmp = java.io.File.createTempFile("lydia", ".pdf", getContext().getCacheDir());
                java.io.FileOutputStream fo = new java.io.FileOutputStream(tmp);
                fo.write(bytes);
                fo.close();
                android.os.ParcelFileDescriptor fd = android.os.ParcelFileDescriptor.open(tmp, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
                android.graphics.pdf.PdfRenderer r = new android.graphics.pdf.PdfRenderer(fd);
                int n = Math.min(r.getPageCount(), 4);
                org.json.JSONArray pages = new org.json.JSONArray();
                com.google.mlkit.vision.text.TextRecognizer rec = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS);
                for (int i = 0; i < n; i++) {
                    android.graphics.pdf.PdfRenderer.Page pg = r.openPage(i);
                    float k = 2000f / Math.max(pg.getWidth(), pg.getHeight());
                    int w = Math.max(1, Math.round(pg.getWidth() * k)), h = Math.max(1, Math.round(pg.getHeight() * k));
                    android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
                    bmp.eraseColor(android.graphics.Color.WHITE);
                    pg.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    pg.close();
                    com.google.mlkit.vision.text.Text text = com.google.android.gms.tasks.Tasks.await(rec.process(com.google.mlkit.vision.common.InputImage.fromBitmap(bmp, 0)));
                    org.json.JSONArray words = new org.json.JSONArray();
                    for (com.google.mlkit.vision.text.Text.TextBlock b : text.getTextBlocks())
                        for (com.google.mlkit.vision.text.Text.Line l : b.getLines())
                            for (com.google.mlkit.vision.text.Text.Element e : l.getElements()) {
                                android.graphics.Rect rc = e.getBoundingBox();
                                if (rc == null) continue;
                                org.json.JSONObject o = new org.json.JSONObject();
                                o.put("t", e.getText());
                                o.put("x", rc.left);
                                o.put("y", rc.top);
                                o.put("w", rc.width());
                                o.put("h", rc.height());
                                words.put(o);
                            }
                    org.json.JSONObject po = new org.json.JSONObject();
                    po.put("w", w);
                    po.put("h", h);
                    po.put("words", words);
                    pages.put(po);
                    bmp.recycle();
                }
                r.close();
                fd.close();
                com.getcapacitor.JSObject res = new com.getcapacitor.JSObject();
                res.put("pages", pages.toString());
                call.resolve(res);
            } catch (Exception e) {
                call.reject("Non riesco a leggere questo PDF");
            } finally {
                if (tmp != null) tmp.delete();
            }
        }).start();
    }

    /** Notifica una volta sola per chiave (prezzo obiettivo raggiunto, liquidità sopra la riserva). */
    @PluginMethod
    public void notifyOnce(PluginCall call) {
        boolean sent = LydiaNotify.once(getContext(), call.getString("key", ""), call.getString("title", "Lydia"), call.getString("body", ""), call.getString("link", "patrimonio"));
        com.getcapacitor.JSObject r = new com.getcapacitor.JSObject();
        r.put("sent", sent);
        call.resolve(r);
    }

    @PluginMethod
    public void notifyReset(PluginCall call) {
        LydiaNotify.reset(getContext(), call.getString("key", ""));
        call.resolve();
    }
}
