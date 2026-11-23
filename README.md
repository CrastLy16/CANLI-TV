# Canlı TV & Radyo TR (Android Projesi)

Türkiye canlı TV ve radyo yayınlarını tek uygulamada toplayan, her açılışta kanal listelerini güncelleyip mevcut ağdaki erişilebilirliğini kontrol eden Kotlin + Jetpack Compose + Media3 (ExoPlayer) Android uygulamasıdır.

## Öne Çıkan Özellikler

- **Her Açılışta Otomatik Güncelleme & Sağlık Kontrolü:**
  - Canlı TV kanalları açık kaynaklı [iptv-org Türkiye listesi](https://github.com/iptv-org/iptv/blob/master/PLAYLISTS.md) (`https://iptv-org.github.io/iptv/countries/tr.m3u`) üzerinden çekilir.
  - Radyo istasyonları açık kaynaklı [Radio Browser API](https://docs.radio-browser.info/) Türkiye kayıtlarından (`de1`, `nl1`, `at1` otomatik sunucu yedekli) alınır.
  - Uygulama her açıldığında linklere paralel hafif bağlantı testi uygulanır; çalışan kanallar **"Aktif"** etiketiyle üste taşınır.
- **Kurum Ağı Uyumlu Çalışma (Güvenlik Duvarı Dostu):**
  - Varsayılan açık gelen **Kurum Ağı (Yalnızca HTTPS 443)** filtresi, yalnızca standart 443 portundaki `https://` yayınlarını listeler.
  - Cihazın sistem proxy ve kurum CA sertifikalarını (`network_security_config.xml`) destekler.
  - Güvenlik duvarını atlatma/VPN mekanizması içermez; kurum ağının izin verdiği resmi yayınlar açılır, erişilemeyenler listede **"Erişilemiyor"** olarak işaretlenir ve tek tuşla gizlenebilir.
- **Favoriler, Arama ve Önbellek:**
  - Liste sunucularına geçici olarak ulaşılamazsa son çalışan kanal listesi cihaz önbelleğinden yüklenir.

## APK Dosyasını Oluşturma (2 Kolay Yol)

### Yöntem 1: Android Studio ile (Bilgisayarda)
1. ZIP arşivini bir klasöre çıkartın.
2. **Android Studio**'yu açın ve **Open** seçeneğiyle `CanliTvRadyoTR` klasörünü seçin.
3. Gradle senkronizasyonu tamamlandıktan sonra üst menüden **Build → Build Bundle(s) / APK(s) → Build APK(s)** yolunu izleyin.
4. Oluşan APK dosyasını `app/build/outputs/apk/debug/app-debug.apk` yolundan alıp Android telefonunuza kurabilirsiniz.

### Yöntem 2: GitHub Actions ile (Android Studio Kurmadan)
1. GitHub'da yeni bir depo (repository) oluşturun ve bu klasördeki tüm dosyaları (`.github` klasörü dahil) yükleyin.
2. Deponun **Actions** sekmesine girin → **Android APK Derle** iş akışını çalıştırın (**Run workflow**).
3. İşlem tamamlandığında sayfanın altındaki **Artifacts** bölümünden `CanliTvRadyoTR-debug-apk` dosyasını indirip telefonunuza kurun.
