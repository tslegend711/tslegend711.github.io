package io.github.tslegend711.lydia;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Widget Investimenti: totale e guadagno, poi ogni titolo con andamento di oggi e guadagno in euro. */
public class LydiaInvWidgetProvider extends AppWidgetProvider {
    static final int[] ROW = {R.id.iv_r1, R.id.iv_r2, R.id.iv_r3, R.id.iv_r4};
    static final int[] NAME = {R.id.iv_n1, R.id.iv_n2, R.id.iv_n3, R.id.iv_n4};
    static final int[] DAY = {R.id.iv_d1, R.id.iv_d2, R.id.iv_d3, R.id.iv_d4};
    static final int[] GAIN = {R.id.iv_g1, R.id.iv_g2, R.id.iv_g3, R.id.iv_g4};
    static final int[] SUB = {R.id.iv_m1, R.id.iv_m2, R.id.iv_m3, R.id.iv_m4};
    static final int[] PRICE = {R.id.iv_p1, R.id.iv_p2, R.id.iv_p3, R.id.iv_p4};
    static final int[] GPCT = {R.id.iv_q1, R.id.iv_q2, R.id.iv_q3, R.id.iv_q4};

    static final String ACTION_REFRESH = "io.github.tslegend711.lydia.REFRESH";
    static volatile boolean busy = false;

    @Override
    public void onReceive(Context c, android.content.Intent intent) {
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            busy = true;
            LydiaWidgetProvider.updateAll(c);
            fetch(c);
            return;
        }
        super.onReceive(c, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, build(context));
        // ogni mezz'ora: prezzi nuovi da Yahoo anche con l'app chiusa
        fetch(context);
    }

    void fetch(Context context) {
        final Context app = context.getApplicationContext();
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                if (refresh(app)) {
                    AppWidgetManager m = AppWidgetManager.getInstance(app);
                    for (int id : m.getAppWidgetIds(new ComponentName(app, LydiaInvWidgetProvider.class))) m.updateAppWidget(id, build(app));
                }
            } catch (Exception ignored) {
            } finally {
                busy = false;
                LydiaWidgetProvider.updateAll(app);
                pr.finish();
            }
        }).start();
    }

    static int color(Context c, int sign) {
        return c.getColor(sign > 0 ? R.color.lydia_w_pos : sign < 0 ? R.color.lydia_w_neg : R.color.lydia_w_muted);
    }

    static String eur(double v) {
        NumberFormat f = NumberFormat.getIntegerInstance(Locale.ITALY);
        return f.format(Math.round(Math.abs(v))) + " €";
    }

    static String money(double v) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.ITALY);
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return f.format(v) + " €";
    }

    static String signed(double v) {
        return (v >= 0 ? "+" : "−") + eur(v);
    }

    static String pct(double v) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.ITALY);
        f.setMinimumFractionDigits(1);
        f.setMaximumFractionDigits(1);
        return (v >= 0 ? "+" : "−") + f.format(Math.abs(v * 100)) + "%";
    }

    static int sgn(double v) {
        return v > 0.0049 ? 1 : v < -0.0049 ? -1 : 0;
    }

    /** Scarica i prezzi dei titoli con un simbolo e ricalcola i numeri del widget. */
    static boolean refresh(Context c) throws Exception {
        String raw = c.getSharedPreferences(LydiaWidgetProvider.PREFS, Context.MODE_PRIVATE).getString("data", "");
        if (raw == null || raw.length() == 0) return false;
        JSONObject all = new JSONObject(raw);
        JSONObject inv = all.optJSONObject("inv");
        if (inv == null) return false;
        JSONArray rows = inv.optJSONArray("rows");
        if (rows == null || rows.length() == 0) return false;
        double tv = 0, tc = 0;
        boolean any = false, complete = true;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject r = rows.getJSONObject(i);
            String sym = r.optString("sym", "");
            double q = r.optDouble("q", 0), cost = r.optDouble("c", 0);
            double[] pp = sym.length() > 0 ? price(sym) : null;
            if (pp == null) {
                complete = false;
                continue;
            }
            any = true;
            double v = q * pp[0], g = v - cost;
            r.put("p", money(pp[0]));
            tv += v;
            tc += cost;
            if (pp[1] > 0) {
                double ch = pp[0] / pp[1] - 1;
                r.put("d", (ch >= 0 ? "▲ " : "▼ ") + pct(ch).substring(1));
                r.put("ds", sgn(ch * 100));
            }
            r.put("g", signed(g));
            r.put("gp", pct(cost > 0 ? g / cost : 0));
            r.put("s", sgn(g));
        }
        if (!any) return false;
        if (complete) {
            double g = tv - tc;
            inv.put("total", eur(tv));
            inv.put("gain", signed(g) + " · " + pct(tc > 0 ? g / tc : 0));
            inv.put("gainp", pct(tc > 0 ? g / tc : 0));
            inv.put("sign", sgn(g));
        }
        inv.put("upd", "prezzi " + new SimpleDateFormat("HH:mm", Locale.ITALY).format(new Date()));
        c.getSharedPreferences(LydiaWidgetProvider.PREFS, Context.MODE_PRIVATE).edit().putString("data", all.toString()).apply();
        return true;
    }

    /** Prezzo attuale e chiusura del giorno prima. */
    static double[] price(String sym) {
        HttpURLConnection h = null;
        try {
            URL u = new URL("https://query1.finance.yahoo.com/v8/finance/chart/" + URLEncoder.encode(sym, "UTF-8") + "?range=5d&interval=1d");
            h = (HttpURLConnection) u.openConnection();
            h.setConnectTimeout(8000);
            h.setReadTimeout(8000);
            h.setRequestProperty("User-Agent", "Mozilla/5.0");
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(h.getInputStream(), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            JSONObject res = new JSONObject(sb.toString()).getJSONObject("chart").getJSONArray("result").getJSONObject(0);
            double p = res.getJSONObject("meta").optDouble("regularMarketPrice", 0);
            if (!(p > 0)) return null;
            double prev = 0, lastClose = 0;
            JSONArray cl = res.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).optJSONArray("close");
            if (cl != null) {
                int n = 0;
                for (int i = cl.length() - 1; i >= 0; i--) {
                    if (cl.isNull(i)) continue;
                    n++;
                    if (n == 1) lastClose = cl.optDouble(i, 0);
                    if (n == 2) {
                        prev = cl.optDouble(i, 0);
                        break;
                    }
                }
            }
            // l'ultima chiusura della serie è il prezzo giusto della Borsa scelta
            if (lastClose > 0) p = lastClose;
            if (!(prev > 0)) prev = res.getJSONObject("meta").optDouble("previousClose", 0);
            return new double[]{p, prev};
        } catch (Exception e) {
            return null;
        } finally {
            if (h != null) h.disconnect();
        }
    }

    static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.lydia_inv_widget);
        JSONObject j = LydiaWidgetProvider.data(c, "inv");
        boolean pv = LydiaWidgetProvider.priv(c), hid = LydiaWidgetProvider.hidden(c);
        v.setOnClickPendingIntent(R.id.iv_root, LydiaWidgetProvider.link(c, "patrimonio", 20));
        v.setViewVisibility(R.id.iv_eye, pv ? View.VISIBLE : View.GONE);
        v.setImageViewResource(R.id.iv_eye, hid ? R.drawable.lydia_ic_eye : R.drawable.lydia_ic_eye_off);
        v.setOnClickPendingIntent(R.id.iv_eye, LydiaWidgetProvider.action(c, LydiaWidgetProvider.ACTION_REVEAL, 30));
        android.content.Intent ri = new android.content.Intent(ACTION_REFRESH);
        ri.setClass(c, LydiaInvWidgetProvider.class);
        v.setOnClickPendingIntent(R.id.iv_ref, android.app.PendingIntent.getBroadcast(c, 32, ri, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE));
        JSONArray rows = j != null ? j.optJSONArray("rows") : null;
        if (j == null || rows == null || rows.length() == 0) {
            v.setTextViewText(R.id.iv_total, j == null ? "Apri Lydia" : "Nessun titolo");
            v.setTextViewText(R.id.iv_gain, j == null ? "Il widget si riempie alla prima apertura" : "Aggiungi un titolo in Patrimonio");
            v.setTextColor(R.id.iv_gain, c.getColor(R.color.lydia_w_muted));
            v.setTextViewText(R.id.iv_upd, "");
            for (int r : ROW) v.setViewVisibility(r, View.GONE);
            return v;
        }
        int sg = j.optInt("sign", 0);
        v.setTextViewText(R.id.iv_total, hid ? "•••••• €" : j.optString("total", ""));
        v.setTextViewText(R.id.iv_gain, hid ? j.optString("gainp", "") : j.optString("gain", ""));
        v.setTextColor(R.id.iv_gain, color(c, sg));
        v.setTextViewText(R.id.iv_upd, busy ? "aggiorno…" : j.optString("upd", ""));
        for (int i = 0; i < ROW.length; i++) {
            JSONObject r = rows.optJSONObject(i);
            if (r == null) {
                v.setViewVisibility(ROW[i], View.GONE);
                continue;
            }
            v.setViewVisibility(ROW[i], View.VISIBLE);
            v.setTextViewText(NAME[i], r.optString("n", ""));
            String d = r.optString("d", "");
            v.setTextViewText(DAY[i], d.length() > 0 ? d : "–");
            v.setTextColor(DAY[i], color(c, r.optInt("ds", 0)));
            v.setTextViewText(SUB[i], r.optString("nm", ""));
            v.setTextViewText(PRICE[i], r.optString("p", "–"));
            v.setTextViewText(GAIN[i], hid ? r.optString("gp", "") : r.optString("g", ""));
            v.setTextColor(GAIN[i], color(c, r.optInt("s", 0)));
            v.setTextViewText(GPCT[i], hid ? "" : r.optString("gp", ""));
            v.setTextColor(GPCT[i], color(c, r.optInt("s", 0)));
        }
        return v;
    }
}
