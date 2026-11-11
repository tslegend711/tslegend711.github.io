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
# impronta per sbloccare l'app
g = root / 'android' / 'app' / 'build.gradle'
gs = g.read_text(encoding='utf-8')
if 'androidx.biometric' not in gs:
    gs = gs.replace('dependencies {', 'dependencies {\n    implementation "androidx.biometric:biometric:1.1.0"\n    implementation "com.google.android.gms:play-services-mlkit-text-recognition:19.0.1"', 1)
    g.write_text(gs, encoding='utf-8')
# lettura delle foto: il modello lo scarica Google Play, l'app resta leggera
ms = m.read_text(encoding='utf-8')
if 'com.google.mlkit.vision.DEPENDENCIES' not in ms:
    ms = ms.replace('</application>', '    <meta-data android:name="com.google.mlkit.vision.DEPENDENCIES" android:value="ocr" />\n    </application>', 1)
    m.write_text(ms, encoding='utf-8')
# backup di Android: solo la copia dei dati che Lydia scrive in files/lydia_backup.json
ms = m.read_text(encoding='utf-8')
if 'lydia_backup_rules' not in ms:
    ms = re.sub(r'\s*android:(fullBackupContent|dataExtractionRules)="[^"]*"', '', ms)
    if 'android:allowBackup=' in ms:
        ms = re.sub(r'android:allowBackup="[^"]*"', 'android:allowBackup="true"', ms, count=1)
    else:
        ms = ms.replace('<application', '<application\n        android:allowBackup="true"', 1)
    ms = ms.replace('android:allowBackup="true"', 'android:allowBackup="true"\n        android:fullBackupContent="@xml/lydia_backup_rules"\n        android:dataExtractionRules="@xml/lydia_data_rules"', 1)
    m.write_text(ms, encoding='utf-8')
print('progetto Android aggiornato:', m)
