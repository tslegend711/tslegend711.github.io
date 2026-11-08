package io.github.tslegend711.lydia;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;

/** Widget Investimenti: valore totale, guadagno e fino a quattro ETF o azioni. */
public class LydiaInvWidgetProvider extends AppWidgetProvider {
    static final int POS = 0xFF3DD68C, NEG = 0xFFF07178, MUTED = 0xFFB4BCCD;
    static final int[] ROW = {R.id.iv_r1, R.id.iv_r2, R.id.iv_r3, R.id.iv_r4};
    static final int[] NAME = {R.id.iv_n1, R.id.iv_n2, R.id.iv_n3, R.id.iv_n4};
    static final int[] PRICE = {R.id.iv_p1, R.id.iv_p2, R.id.iv_p3, R.id.iv_p4};
    static final int[] GAIN = {R.id.iv_g1, R.id.iv_g2, R.id.iv_g3, R.id.iv_g4};

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, build(context));
    }

    static int color(int sign) {
        return sign > 0 ? POS : sign < 0 ? NEG : MUTED;
    }

    static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.lydia_inv_widget);
        JSONObject j = LydiaWidgetProvider.data(c, "inv");
        v.setOnClickPendingIntent(R.id.iv_root, LydiaWidgetProvider.link(c, "patrimonio", 20));
        if (j == null) {
            v.setTextViewText(R.id.iv_total, "Apri Lydia");
            v.setTextViewText(R.id.iv_gain, "");
            v.setTextViewText(R.id.iv_upd, "");
            for (int r : ROW) v.setViewVisibility(r, View.GONE);
            return v;
        }
        v.setTextViewText(R.id.iv_total, j.optString("total", ""));
        v.setTextViewText(R.id.iv_gain, j.optString("gain", ""));
        v.setTextColor(R.id.iv_gain, color(j.optInt("sign", 0)));
        v.setTextViewText(R.id.iv_upd, j.optString("upd", ""));
        JSONArray rows = j.optJSONArray("rows");
        for (int i = 0; i < ROW.length; i++) {
            JSONObject r = rows != null ? rows.optJSONObject(i) : null;
            if (r == null) {
                v.setViewVisibility(ROW[i], View.GONE);
                continue;
            }
            v.setViewVisibility(ROW[i], View.VISIBLE);
            v.setTextViewText(NAME[i], r.optString("n", ""));
            v.setTextViewText(PRICE[i], r.optString("p", ""));
            v.setTextViewText(GAIN[i], r.optString("g", ""));
            v.setTextColor(GAIN[i], color(r.optInt("s", 0)));
        }
        return v;
    }
}
