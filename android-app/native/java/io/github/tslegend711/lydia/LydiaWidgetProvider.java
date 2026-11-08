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

/** Widget: totale dei conti, i conti principali e i pulsanti Spesa, Entrata, Giroconto. */
public class LydiaWidgetProvider extends AppWidgetProvider {
    static final String PREFS = "lydia_widget";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, build(context));
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, LydiaWidgetProvider.class));
        for (int id : ids) m.updateAppWidget(id, build(c));
    }

    static PendingIntent link(Context c, String what, int req) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("lydia://" + what));
        i.setClass(c, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.lydia_widget);
        String raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("data", "");
        String total = "Apri Lydia";
        String lines = "Il riepilogo compare dopo la prima apertura";
        String upd = "";
        try {
            if (raw != null && raw.length() > 0) {
                JSONObject j = new JSONObject(raw);
                total = j.optString("total", total);
                lines = j.optString("lines", "");
                upd = j.optString("upd", "");
            }
        } catch (Exception ignored) { }
        v.setTextViewText(R.id.w_total, total);
        v.setTextViewText(R.id.w_lines, lines);
        v.setTextViewText(R.id.w_upd, upd);
        v.setOnClickPendingIntent(R.id.w_root, link(c, "home", 10));
        v.setOnClickPendingIntent(R.id.w_out, link(c, "nuova-spesa", 11));
        v.setOnClickPendingIntent(R.id.w_in, link(c, "nuova-entrata", 12));
        v.setOnClickPendingIntent(R.id.w_tr, link(c, "giroconto", 13));
        return v;
    }
}
