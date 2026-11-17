package io.github.tslegend711.lydia;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Widget Watchlist: i titoli seguiti (fino a 5) con prezzo e andamento di oggi. Si aggiorna da solo ogni mezz'ora. */
public class LydiaWatchWidgetProvider extends AppWidgetProvider {
    static final int[] ROW = {R.id.wl_r1, R.id.wl_r2, R.id.wl_r3, R.id.wl_r4, R.id.wl_r5};
    static final int[] NAME = {R.id.wl_n1, R.id.wl_n2, R.id.wl_n3, R.id.wl_n4, R.id.wl_n5};
    static final int[] SUB = {R.id.wl_m1, R.id.wl_m2, R.id.wl_m3, R.id.wl_m4, R.id.wl_m5};
    static final int[] PRICE = {R.id.wl_p1, R.id.wl_p2, R.id.wl_p3, R.id.wl_p4, R.id.wl_p5};
    static final int[] DAY = {R.id.wl_d1, R.id.wl_d2, R.id.wl_d3, R.id.wl_d4, R.id.wl_d5};
    static final String ACTION_REFRESH = "io.github.tslegend711.lydia.WATCH_REFRESH";
    static volatile boolean busy = false;

    @Override
    public void onReceive(Context c, Intent intent) {
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            busy = true;
            redraw(c);
            fetch(c);
            return;
        }
        super.onReceive(c, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, build(context));
        fetch(context);
    }

    static void redraw(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (int id : m.getAppWidgetIds(new ComponentName(c, LydiaWatchWidgetProvider.class))) m.updateAppWidget(id, build(c));
    }

    void fetch(Context context) {
        final Context app = context.getApplicationContext();
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                refresh(app);
            } catch (Exception ignored) {
            } finally {
                busy = false;
                redraw(app);
                pr.finish();
            }
        }).start();
    }

    static String num(double v) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.ITALY);
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return f.format(v);
    }

    /** Prezzi nuovi da Yahoo per i titoli della watchlist (parte solo il simbolo del titolo). */
    static void refresh(Context c) throws Exception {
        android.content.SharedPreferences sp = c.getSharedPreferences(LydiaWidgetProvider.PREFS, Context.MODE_PRIVATE);
        String raw = sp.getString("data", "");
        if (raw == null || raw.length() == 0) return;
        JSONObject all = new JSONObject(raw);
        JSONObject w = all.optJSONObject("watch");
        JSONArray rows = w != null ? w.optJSONArray("rows") : null;
        if (rows == null || rows.length() == 0) return;
        boolean any = false;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject r = rows.getJSONObject(i);
            String sym = r.optString("sym", "");
            if (sym.length() == 0) continue;
            double[] pp = LydiaInvWidgetProvider.price(sym);
            if (pp == null) continue;
            any = true;
            r.put("p", num(pp[0]) + " " + r.optString("cs", "€"));
            if (pp[1] > 0) {
                double ch = pp[0] / pp[1] - 1;
                r.put("d", (ch >= 0 ? "▲ " : "▼ ") + LydiaInvWidgetProvider.pct(ch).substring(1));
                r.put("ds", LydiaInvWidgetProvider.sgn(ch * 100));
            }
        }
        if (!any) return;
        w.put("upd", "prezzi " + new SimpleDateFormat("HH:mm", Locale.ITALY).format(new Date()));
        // rilegge i dati: l'app potrebbe averli cambiati mentre si scaricavano i prezzi
        String now = sp.getString("data", "");
        if (now != null && now.length() > 0 && !now.equals(raw)) {
            JSONObject fresh = new JSONObject(now);
            fresh.put("watch", w);
            all = fresh;
        }
        sp.edit().putString("data", all.toString()).apply();
    }

    static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.lydia_watch_widget);
        v.setInt(R.id.wl_bg, "setImageAlpha", LydiaWidgetProvider.alpha(c));
        v.setOnClickPendingIntent(R.id.wl_root, LydiaWidgetProvider.link(c, "patrimonio", 40));
        Intent ri = new Intent(ACTION_REFRESH);
        ri.setClass(c, LydiaWatchWidgetProvider.class);
        v.setOnClickPendingIntent(R.id.wl_ref, android.app.PendingIntent.getBroadcast(c, 41, ri, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE));
        JSONObject j = LydiaWidgetProvider.data(c, "watch");
        JSONArray rows = j != null ? j.optJSONArray("rows") : null;
        v.setTextViewText(R.id.wl_upd, busy ? "aggiorno…" : j != null ? j.optString("upd", "") : "");
        boolean empty = rows == null || rows.length() == 0;
        v.setViewVisibility(R.id.wl_empty, empty ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.wl_empty, j == null ? "Apri Lydia: il widget si riempie alla prima apertura" : "Aggiungi un titolo alla watchlist in Patrimonio");
        for (int i = 0; i < ROW.length; i++) {
            JSONObject r = rows != null ? rows.optJSONObject(i) : null;
            if (r == null) {
                v.setViewVisibility(ROW[i], View.GONE);
                continue;
            }
            v.setViewVisibility(ROW[i], View.VISIBLE);
            v.setTextViewText(NAME[i], r.optString("n", ""));
            v.setTextViewText(SUB[i], r.optString("nm", ""));
            v.setViewVisibility(SUB[i], r.optString("nm", "").length() > 0 ? View.VISIBLE : View.GONE);
            String p = r.optString("p", "");
            v.setTextViewText(PRICE[i], p.length() > 0 ? p : "–");
            String d = r.optString("d", "");
            v.setTextViewText(DAY[i], d.length() > 0 ? d : "–");
            v.setTextColor(DAY[i], LydiaInvWidgetProvider.color(c, r.optInt("ds", 0)));
        }
        return v;
    }
}
