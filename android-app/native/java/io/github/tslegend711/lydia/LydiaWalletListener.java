package io.github.tslegend711.lydia;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Legge SOLO le notifiche delle app Wallet scelte (Google Wallet, Samsung Wallet).
 * Non manda nulla da nessuna parte: le mette in coda sul telefono e Lydia le trasforma
 * in spese la prossima volta che si apre.
 */
public class LydiaWalletListener extends NotificationListenerService {
    static final String PREF = "lydia_wallet";
    static final String DEF_PKGS = "com.google.android.apps.walletnfcrel,com.samsung.android.spay,com.samsung.android.samsungpay.gear";

    static String pkgs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("pkgs", DEF_PKGS);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null) return;
            String pkg = sbn.getPackageName();
            if (pkg == null || !("," + pkgs(this) + ",").contains("," + pkg + ",")) return;
            SharedPreferences p = getSharedPreferences(PREF, Context.MODE_PRIVATE);
            if (!p.getBoolean("on", false)) return;
            Notification n = sbn.getNotification();
            if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            Bundle x = n.extras;
            String title = str(x, Notification.EXTRA_TITLE);
            String text = str(x, Notification.EXTRA_TEXT);
            String big = str(x, Notification.EXTRA_BIG_TEXT);
            String sub = str(x, Notification.EXTRA_SUB_TEXT);
            if (title.isEmpty() && text.isEmpty() && big.isEmpty()) return;
            String key = sbn.getKey() + "|" + sbn.getPostTime();
            String sig = (title + "|" + text + "|" + big).trim();
            synchronized (LydiaWalletListener.class) {
                // la stessa notifica aggiornata più volte arriva di nuovo: tengo solo la prima
                if (sig.equals(p.getString("lastSig", "")) && Math.abs(System.currentTimeMillis() - p.getLong("lastAt", 0)) < 120000) return;
                JSONArray q = new JSONArray(p.getString("queue", "[]"));
                JSONObject o = new JSONObject();
                o.put("id", Long.toString(sbn.getPostTime(), 36) + Integer.toString(Math.abs(sig.hashCode()), 36));
                o.put("pkg", pkg);
                o.put("t", title);
                o.put("x", text);
                o.put("b", big);
                o.put("s", sub);
                o.put("time", sbn.getPostTime());
                q.put(o);
                JSONArray keep = new JSONArray();
                for (int i = Math.max(0, q.length() - 200); i < q.length(); i++) keep.put(q.get(i));
                p.edit().putString("queue", keep.toString()).putString("lastSig", sig).putLong("lastAt", System.currentTimeMillis()).apply();
            }
        } catch (Exception e) {
            // una notifica illeggibile non deve mai far cadere il servizio
        }
    }

    private static String str(Bundle b, String k) {
        if (b == null) return "";
        CharSequence c = b.getCharSequence(k);
        return c == null ? "" : c.toString();
    }
}
