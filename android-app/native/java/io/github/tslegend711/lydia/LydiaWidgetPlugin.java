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
import com.getcapacitor.annotation.ActivityCallback;
import androidx.activity.result.ActivityResult;
import android.content.Intent;
import android.net.Uri;

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

    // ---------- composizione degli ETF (Yahoo vuole un "crumb" legato a un cookie: lo prendo una volta e lo riuso) ----------
    private static String crumb = null;
    private static java.net.CookieManager jar = null;

    private static String httpGet(String url) throws Exception {
        java.net.HttpURLConnection h = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        h.setConnectTimeout(9000);
        h.setReadTimeout(9000);
        h.setInstanceFollowRedirects(true);
        h.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36");
        h.setRequestProperty("Accept", "*/*");
        int code = h.getResponseCode();
        java.io.InputStream in = code >= 400 ? h.getErrorStream() : h.getInputStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
        }
        h.disconnect();
        if (code >= 400) throw new java.io.IOException("HTTP " + code);
        return sb.toString();
    }

    private static synchronized String yahooCrumb(boolean fresh) throws Exception {
        if (jar == null) {
            jar = new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL);
            java.net.CookieHandler.setDefault(jar);
        }
        if (crumb != null && !fresh) return crumb;
        try { httpGet("https://fc.yahoo.com"); } catch (Exception e) { /* risponde 404 ma lascia il cookie */ }
        String c = httpGet("https://query2.finance.yahoo.com/v1/test/getcrumb").trim();
        if (c.isEmpty() || c.contains("<")) throw new java.io.IOException("crumb");
        crumb = c;
        return crumb;
    }

    @PluginMethod
    public void yahooSummary(final PluginCall call) {
        final String sym = call.getString("sym", "");
        final String modules = call.getString("modules", "topHoldings,fundProfile");
        new Thread(() -> {
            String body = null;
            for (int attempt = 0; attempt < 2 && body == null; attempt++) {
                try {
                    String c = yahooCrumb(attempt > 0);
                    body = httpGet("https://query2.finance.yahoo.com/v10/finance/quoteSummary/" + java.net.URLEncoder.encode(sym, "UTF-8") + "?modules=" + modules + "&crumb=" + java.net.URLEncoder.encode(c, "UTF-8"));
                } catch (Exception e) {
                    body = null;
                }
            }
            if (body == null) {
                call.reject("Yahoo non risponde");
                return;
            }
            com.getcapacitor.JSObject r = new com.getcapacitor.JSObject();
            r.put("json", body);
            call.resolve(r);
        }).start();
    }

    // ---------- copia su Google Drive (o in qualsiasi app di file): l'utente sceglie il file una volta con la finestra di Android,
    // poi Lydia lo riscrive da sola. Nessun accesso all'account: Android concede il permesso solo su quel file. ----------
    private static final String BK = "lydia_backup";

    @PluginMethod
    public void driveLink(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, call.getString("name", "Lydia_backup.json"));
        startActivityForResult(call, i, "driveLinked");
    }

    @ActivityCallback
    private void driveLinked(PluginCall call, ActivityResult result) {
        com.getcapacitor.JSObject r = new com.getcapacitor.JSObject();
        Intent d = result == null ? null : result.getData();
        Uri u = d == null ? null : d.getData();
        if (result == null || result.getResultCode() != Activity.RESULT_OK || u == null) {
            r.put("ok", false);
            call.resolve(r);
            return;
        }
        Context c = getContext();
        try {
            c.getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception e) { /* alcune app non lo concedono: proverò comunque a scrivere */ }
        c.getSharedPreferences(BK, Context.MODE_PRIVATE).edit().putString("uri", u.toString()).putLong("last", 0).apply();
        r.put("ok", true);
        r.put("name", displayName(c, u));
        r.put("where", where(u));
        call.resolve(r);
    }

    private static String displayName(Context c, Uri u) {
        android.database.Cursor q = null;
        try {
            q = c.getContentResolver().query(u, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (q != null && q.moveToFirst()) return q.getString(0);
        } catch (Exception e) {
        } finally {
            if (q != null) q.close();
        }
        return "Lydia_backup.json";
    }

    private static String where(Uri u) {
        String a = u.getAuthority() == null ? "" : u.getAuthority();
        if (a.contains("google.android.apps.docs")) return "Google Drive";
        if (a.contains("onedrive")) return "OneDrive";
        if (a.contains("dropbox")) return "Dropbox";
        if (a.contains("externalstorage") || a.contains("downloads") || a.contains("media")) return "telefono";
        return "app di file";
    }

    @PluginMethod
    public void driveInfo(PluginCall call) {
        Context c = getContext();
        android.content.SharedPreferences p = c.getSharedPreferences(BK, Context.MODE_PRIVATE);
        String s = p.getString("uri", null);
        com.getcapacitor.JSObject r = new com.getcapacitor.JSObject();
        r.put("linked", s != null);
        if (s != null) {
            Uri u = Uri.parse(s);
            r.put("name", displayName(c, u));
            r.put("where", where(u));
            r.put("last", p.getLong("last", 0));
            r.put("error", p.getString("err", ""));
        }
        call.resolve(r);
    }

    @PluginMethod
    public void driveUnlink(PluginCall call) {
        Context c = getContext();
        android.content.SharedPreferences p = c.getSharedPreferences(BK, Context.MODE_PRIVATE);
        String s = p.getString("uri", null);
        if (s != null) {
            try {
                c.getContentResolver().releasePersistableUriPermission(Uri.parse(s), Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception e) { }
        }
        p.edit().clear().apply();
        call.resolve();
    }

    /** Riscrive il file scelto. Se l'app di destinazione non accetta "wt", scrivo e poi taglio la coda. */
    @PluginMethod
    public void driveWrite(final PluginCall call) {
        final String data = call.getString("data", "");
        final Context c = getContext();
        new Thread(() -> {
            android.content.SharedPreferences p = c.getSharedPreferences(BK, Context.MODE_PRIVATE);
            String s = p.getString("uri", null);
            com.getcapacitor.JSObject r = new com.getcapacitor.JSObject();
            if (s == null || data.isEmpty()) {
                r.put("ok", false);
                r.put("error", "nessun file");
                call.resolve(r);
                return;
            }
            Uri u = Uri.parse(s);
            byte[] b;
            try { b = data.getBytes("UTF-8"); } catch (Exception e) { b = data.getBytes(); }
            String err = null;
            try {
                java.io.OutputStream o = c.getContentResolver().openOutputStream(u, "wt");
                if (o == null) throw new java.io.IOException("null");
                o.write(b);
                o.flush();
                o.close();
            } catch (Exception e1) {
                try {
                    android.os.ParcelFileDescriptor fd = c.getContentResolver().openFileDescriptor(u, "rw");
                    if (fd == null) throw new java.io.IOException("null");
                    java.io.FileOutputStream fo = new java.io.FileOutputStream(fd.getFileDescriptor());
                    fo.getChannel().position(0);
                    fo.write(b);
                    fo.getChannel().truncate(b.length);
                    fo.flush();
                    fo.close();
                    fd.close();
                } catch (Exception e2) {
                    err = (e1 instanceof SecurityException || e2 instanceof SecurityException) ? "permesso" : "scrittura";
                }
            }
            long now = System.currentTimeMillis();
            if (err == null) p.edit().putLong("last", now).putString("err", "").apply();
            else p.edit().putString("err", err).apply();
            r.put("ok", err == null);
            r.put("time", now);
            if (err != null) r.put("error", err);
            call.resolve(r);
        }).start();
    }

    // ---------- copia interna per il backup di Android (Impostazioni > Google > Backup) ----------
    @PluginMethod
    public void saveSnapshot(final PluginCall call) {
        final String data = call.getString("data", "");
        final Context c = getContext();
        new Thread(() -> {
            try {
                java.io.File t = new java.io.File(c.getFilesDir(), "lydia_backup.tmp");
                java.io.FileOutputStream fo = new java.io.FileOutputStream(t);
                fo.write(data.getBytes("UTF-8"));
                fo.getFD().sync();
                fo.close();
                java.io.File f = new java.io.File(c.getFilesDir(), "lydia_backup.json");
                if (!t.renameTo(f)) {
                    f.delete();
                    t.renameTo(f);
                }
                try { new android.app.backup.BackupManager(c).dataChanged(); } catch (Exception e) { }
                call.resolve();
            } catch (Exception e) {
                call.reject("snapshot");
            }
        }).start();
    }

    @PluginMethod
    public void readSnapshot(PluginCall call) {
        com.getcapacitor.JSObject r = new com.getcapacitor.JSObject();
        try {
            java.io.File f = new java.io.File(getContext().getFilesDir(), "lydia_backup.json");
            if (f.exists() && f.length() > 0) {
                byte[] b = new byte[(int) f.length()];
                java.io.FileInputStream in = new java.io.FileInputStream(f);
                int n = 0;
                while (n < b.length) {
                    int k = in.read(b, n, b.length - n);
                    if (k < 0) break;
                    n += k;
                }
                in.close();
                r.put("json", new String(b, 0, n, "UTF-8"));
                r.put("time", f.lastModified());
            }
        } catch (Exception e) { }
        call.resolve(r);
    }
}
