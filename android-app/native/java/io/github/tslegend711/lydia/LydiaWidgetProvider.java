package io.github.tslegend711.lydia;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.view.View;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;
import org.json.JSONObject;

/** Widget Movimenti, su una riga: saldo del mese e pulsanti spesa, entrata, giroconto. */
public class LydiaWidgetProvider extends AppWidgetProvider {
    static final String PREFS = "lydia_widget";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, build(context));
    }

    /** Aggiorna tutti i widget di Lydia (movimenti e investimenti). */
    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (int id : m.getAppWidgetIds(new ComponentName(c, LydiaWidgetProvider.class))) m.updateAppWidget(id, build(c));
        for (int id : m.getAppWidgetIds(new ComponentName(c, LydiaInvWidgetProvider.class))) m.updateAppWidget(id, LydiaInvWidgetProvider.build(c));
    }

    static JSONObject data(Context c, String key) {
        try {
            String raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("data", "");
            if (raw == null || raw.length() == 0) return null;
            JSONObject o = new JSONObject(raw).optJSONObject(key);
            return o;
        } catch (Exception e) {
            return null;
        }
    }

    static PendingIntent link(Context c, String what, int req) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("lydia://" + what));
        i.setClass(c, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static final String ACTION_REVEAL = "io.github.tslegend711.lydia.REVEAL";
    static final String ACTION_HIDE = "io.github.tslegend711.lydia.HIDE";
    static final long REVEAL_MS = 30000;

    /** Widget riservati: gli importi si vedono solo dopo aver toccato l'occhio, per 30 secondi. */
    static boolean priv(Context c) {
        try {
            String raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("data", "");
            return raw != null && raw.length() > 0 && new JSONObject(raw).optBoolean("priv", false);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean hidden(Context c) {
        return priv(c) && c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("revealUntil", 0) < System.currentTimeMillis();
    }

    static PendingIntent action(Context c, String a, int req) {
        Intent i = new Intent(a);
        i.setClass(c, LydiaWidgetProvider.class);
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent != null ? intent.getAction() : null;
        if (ACTION_REVEAL.equals(a)) {
            boolean showing = !hidden(c);
            long until = showing ? 0 : System.currentTimeMillis() + REVEAL_MS;
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("revealUntil", until).apply();
            if (until > 0) {
                try {
                    AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
                    if (am != null) am.set(AlarmManager.RTC, until + 500, action(c, ACTION_HIDE, 31));
                } catch (Exception ignored) { }
            }
            updateAll(c);
            return;
        }
        if (ACTION_HIDE.equals(a)) {
            updateAll(c);
            return;
        }
        super.onReceive(c, intent);
    }

    static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.lydia_widget);
        JSONObject j = data(c, "mov");
        boolean pv = priv(c), hid = hidden(c);
        v.setViewVisibility(R.id.w_eye, pv ? View.VISIBLE : View.GONE);
        v.setImageViewResource(R.id.w_eye, hid ? R.drawable.lydia_ic_eye : R.drawable.lydia_ic_eye_off);
        v.setOnClickPendingIntent(R.id.w_eye, action(c, ACTION_REVEAL, 30));
        v.setTextViewText(R.id.w_title, j != null ? j.optString("title", "Questo mese") : "Lydia");
        v.setTextViewText(R.id.w_total, j != null ? (hid ? "•••• €" : j.optString("total", "")) : "Tocca per aprire");
        if (j != null) v.setTextColor(R.id.w_total, c.getColor(!hid && j.optBoolean("neg", false) ? R.color.lydia_w_neg : R.color.lydia_w_text));
        v.setOnClickPendingIntent(R.id.w_root, link(c, "movimenti", 10));
        v.setOnClickPendingIntent(R.id.w_out, link(c, "nuova-spesa", 11));
        v.setOnClickPendingIntent(R.id.w_in, link(c, "nuova-entrata", 12));
        v.setOnClickPendingIntent(R.id.w_tr, link(c, "giroconto", 13));
        return v;
    }
}
