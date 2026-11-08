package io.github.tslegend711.lydia;

import android.os.Bundle;
import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(LydiaWidgetPlugin.class);
        super.onCreate(savedInstanceState);
        // Barre di sistema e tastiera: il contenuto resta sempre visibile, anche con Android 15 a schermo intero.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View root = findViewById(android.R.id.content);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                // in alto niente margine: la barra dell'app continua sotto l'ora e le notifiche
                LydiaWidgetPlugin.topInsetPx = bars.top;
                // in basso: sotto la barra delle gesture disegna l'app; con la tastiera aperta lascia spazio alla tastiera
                boolean kb = ime.bottom > bars.bottom;
                LydiaWidgetPlugin.bottomInsetPx = kb ? 0 : bars.bottom;
                v.setPadding(bars.left, 0, bars.right, kb ? ime.bottom : 0);
                return WindowInsetsCompat.CONSUMED;
            });
        }
    }
}
