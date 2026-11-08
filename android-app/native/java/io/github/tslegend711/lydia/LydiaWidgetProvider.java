package io.github.tslegend711.lydia;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;
import org.json.JSONObject;

/** Widget Movimenti: saldo del mese, entrate e uscite, pulsanti Spesa, Entrata, Giroconto. */
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

    static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.lydia_widget);
        JSONObject j = data(c, "mov");
        v.setTextViewText(R.id.w_title, j != null ? j.optString("title", "Lydia · questo mese") : "Lydia · questo mese");
        v.setTextViewText(R.id.w_total, j != null ? j.optString("total", "") : "Apri Lydia");
        v.setTextViewText(R.id.w_lines, j != null ? j.optString("lines", "") : "Il riepilogo compare dopo la prima apertura");
        v.setTextViewText(R.id.w_upd, j != null ? j.optString("upd", "") : "");
        if (j != null) v.setTextColor(R.id.w_total, j.optBoolean("neg", false) ? 0xFFF07178 : 0xFFFFFFFF);
        v.setOnClickPendingIntent(R.id.w_root, link(c, "movimenti", 10));
        v.setOnClickPendingIntent(R.id.w_out, link(c, "nuova-spesa", 11));
        v.setOnClickPendingIntent(R.id.w_in, link(c, "nuova-entrata", 12));
        v.setOnClickPendingIntent(R.id.w_tr, link(c, "giroconto", 13));
        return v;
    }
}
