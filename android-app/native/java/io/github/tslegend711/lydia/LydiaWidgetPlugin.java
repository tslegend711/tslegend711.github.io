package io.github.tslegend711.lydia;

import android.content.Context;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Riceve dal codice web il riepilogo da mostrare nel widget (solo testo già formattato). */
@CapacitorPlugin(name = "LydiaWidget")
public class LydiaWidgetPlugin extends Plugin {
    @PluginMethod
    public void setData(PluginCall call) {
        String data = call.getString("data", "");
        Context c = getContext();
        c.getSharedPreferences(LydiaWidgetProvider.PREFS, Context.MODE_PRIVATE)
                .edit().putString("data", data).apply();
        LydiaWidgetProvider.updateAll(c);
        call.resolve();
    }
}
