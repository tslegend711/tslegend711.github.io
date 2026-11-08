"""Aggiunge al progetto Android generato da Capacitor il widget e i link lydia://."""
import pathlib, re, shutil, sys
root = pathlib.Path(__file__).resolve().parent.parent
app = root / 'android' / 'app' / 'src' / 'main'
# codice Java
dst = app / 'java' / 'io' / 'github' / 'tslegend711' / 'lydia'
dst.mkdir(parents=True, exist_ok=True)
for f in (root / 'native' / 'java' / 'io' / 'github' / 'tslegend711' / 'lydia').glob('*.java'):
    shutil.copy(f, dst / f.name)
# risorse (icone, layout del widget, stringhe)
shutil.copytree(root / 'res', app / 'res', dirs_exist_ok=True)
# manifest
m = app / 'AndroidManifest.xml'
s = m.read_text(encoding='utf-8')
if 'LydiaWidgetProvider' not in s:
    link = ('\n            <intent-filter>\n'
            '                <action android:name="android.intent.action.VIEW" />\n'
            '                <category android:name="android.intent.category.DEFAULT" />\n'
            '                <category android:name="android.intent.category.BROWSABLE" />\n'
            '                <data android:scheme="lydia" />\n'
            '            </intent-filter>\n')
    i = s.index('</activity>')
    s = s[:i] + link + '        ' + s[i:]
    rec = ('\n        <receiver android:name=".LydiaWidgetProvider" android:exported="false">\n'
           '            <intent-filter>\n'
           '                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />\n'
           '            </intent-filter>\n'
           '            <meta-data android:name="android.appwidget.provider" android:resource="@xml/lydia_widget_info" />\n'
           '        </receiver>\n'
           '\n        <receiver android:name=".LydiaInvWidgetProvider" android:exported="false">\n'
           '            <intent-filter>\n'
           '                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />\n'
           '            </intent-filter>\n'
           '            <meta-data android:name="android.appwidget.provider" android:resource="@xml/lydia_inv_widget_info" />\n'
           '        </receiver>\n')
    i = s.index('</application>')
    s = s[:i] + rec + '    ' + s[i:]
    if 'windowSoftInputMode' not in s:
        s = s.replace('android:name=".MainActivity"', 'android:name=".MainActivity"\n            android:windowSoftInputMode="adjustResize"', 1)
    m.write_text(s, encoding='utf-8')
print('progetto Android aggiornato:', m)
