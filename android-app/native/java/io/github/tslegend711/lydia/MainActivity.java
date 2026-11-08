package io.github.tslegend711.lydia;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(LydiaWidgetPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
