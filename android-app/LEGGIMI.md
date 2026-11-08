# Lydia per Android (Capacitor)

Questa cartella trasforma la web app (`../index.html`) in un'app Android vera.
I dati vengono salvati nella memoria dell'app, non in quella di Chrome:
cancellare la cronologia o i dati di Chrome non li tocca.

## Come si compila

Lo fa GitHub da solo (`.github/workflows/android.yml`) a ogni modifica di
`index.html` o di questa cartella. L'APK firmato compare nella pagina
**Releases** del repository.

Servono due segreti nel repository (Settings → Secrets and variables → Actions):

- `LYDIA_KEYSTORE`: la chiave di firma in base64
- `LYDIA_KEYSTORE_PASSWORD`: la sua password

La chiave non va mai messa nel repository. Va conservata: senza la stessa
chiave non si possono installare gli aggiornamenti sopra l'app esistente.
