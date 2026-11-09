package io.github.tslegend711.lydia;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import org.json.JSONArray;
import org.json.JSONObject;

/** Avvisi di Lydia (prezzi obiettivo, liquidità): ogni avviso arriva una volta sola finché non lo cambi. */
public class LydiaNotify {
    static final String CH = "lydia_avvisi";
    static final String PREFS = "lydia_avvisi";

    static void channel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager m = c.getSystemService(NotificationManager.class);
            if (m != null && m.getNotificationChannel(CH) == null) {
                NotificationChannel ch = new NotificationChannel(CH, "Avvisi su prezzi e liquidità", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("Quando un titolo arriva al prezzo che hai scelto o la liquidità supera la riserva");
                m.createNotificationChannel(ch);
            }
        }
    }

    /** Manda la notifica solo se questa chiave non è già stata usata. Ritorna true se l'ha mandata. */
    static boolean once(Context c, String key, String title, String body, String link) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.getBoolean(key, false)) return false;
        try {
            channel(c);
            NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH)
                .setSmallIcon(R.drawable.ic_stat_lydia)
                .setColor(0xFFD4AF37)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(LydiaWidgetProvider.link(c, link, 40 + Math.abs(key.hashCode() % 1000)));
            NotificationManagerCompat.from(c).notify(Math.abs(key.hashCode()), b.build());
        } catch (SecurityException e) {
            return false;
        }
        p.edit().putBoolean(key, true).apply();
        return true;
    }

    /** Toglie il segno "già avvisato", per esempio quando la liquidità torna sotto la riserva. */
    static void reset(Context c, String key) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key).apply();
    }

    /** Controllo dei prezzi obiettivo con i prezzi appena scaricati dal widget. */
    static void checkTargets(Context c, JSONObject inv, java.util.Map<String, Double> prices) {
        JSONArray al = inv.optJSONArray("alerts");
        if (al == null) return;
        for (int i = 0; i < al.length(); i++) {
            JSONObject a = al.optJSONObject(i);
            if (a == null) continue;
            String sym = a.optString("sym", "");
            Double p = prices.get(sym);
            if (p == null) {
                double[] pp = LydiaInvWidgetProvider.price(sym);
                if (pp == null) continue;
                p = pp[0];
                prices.put(sym, p);
            }
            double up = a.optDouble("up", 0), dn = a.optDouble("dn", 0);
            String nm = a.optString("n", sym), id = a.optString("id", "");
            String ps = LydiaInvWidgetProvider.money(p);
            if (up > 0 && p >= up)
                once(c, a.optString("ku", "up:" + id), nm + " è salito a " + ps, "Hai chiesto di avvisarti sopra " + LydiaInvWidgetProvider.money(up) + ". Tocca per aprire il titolo.", "titolo/" + id);
            if (dn > 0 && p <= dn)
                once(c, a.optString("kd", "dn:" + id), nm + " è sceso a " + ps, "Hai chiesto di avvisarti sotto " + LydiaInvWidgetProvider.money(dn) + ". Potrebbe essere il momento di comprare: tocca per aprire il titolo.", "titolo/" + id);
        }
    }
}
