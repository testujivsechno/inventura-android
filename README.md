# Inventura – Android WebView + Camera2

Vývojová verze aplikace Inventura. Zachovává HTML aplikaci a přidává nativní diagnostiku kamer přes Android Camera2 API.

## Co diagnostika rozlišuje

- **WebView video vstupy**: kamery, které Android WebView zpřístupní přes `navigator.mediaDevices.enumerateDevices()` a které lze vybrat pro skenování.
- **Camera2 zařízení**: logické/standardní kamerové ID, jejich orientaci (přední/zadní/externí), hardwarovou úroveň, ohniskové vzdálenosti a rozměr snímače.
- **Fyzické senzory logické kamery**: Android Camera2 může u logické kamery uvést `physicalCameraIds`. Tyto senzory se počítají zvlášť a nejsou automaticky považovány za samostatně volitelné WebView kamery.

Výpis respektuje omezení výrobce: některé telefony fyzické senzory nezpřístupňují nebo je vystavují jen jako součást logické kamery.

## Sestavení APK bez Android Studia

1. Nahraj obsah tohoto ZIPu do kořene repozitáře GitHubu.
2. Otevři **Actions** a spusť workflow **Build Inventura APK** (nebo proveď push do `main`).
3. Po úspěšném sestavení otevři běh workflow a stáhni artefakt `inventura-debug-apk`.
4. Rozbal ZIP artefaktu a nainstaluj `app-debug.apk` do telefonu.

## Lokální sestavení

Vyžaduje JDK 17, Android SDK Platform 35 a Gradle 8.11.1:

```bash
gradle assembleDebug
```

## Poznámky

- Aplikace používá AndroidX WebKit `WebViewAssetLoader` a načítá lokální HTML z důvěryhodného HTTPS originu `appassets.androidplatform.net`, aby fungovalo `getUserMedia()`.
- První spuštění vyžaduje oprávnění ke kameře.
- Exporty se na Androidu 10+ ukládají do `Download/Inventura`; na starších systémech do aplikační složky Downloads.
- Jde o vývojovou debug APK, nikoli podepsané produkční vydání.
