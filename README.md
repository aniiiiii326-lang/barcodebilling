# Barcode Biller v2

Upgraded Android barcode billing app.

Features:
- Camera barcode scanner using Google ML Kit
- Product lookup using Open Food Facts
- Local shop product/price database stored in SharedPreferences
- Add products manually
- Quantity +/- controls
- Discount percentage
- GST percentage
- Subtotal, discount, GST and grand total
- Save scanned products to the local catalog
- Scan history
- Share invoice as text
- Product search in local catalog
- GitHub Actions APK build

Important:
A barcode itself does not universally contain a current selling price. The app can look up product information online, while the shop's price can be stored in the local catalog.

Build:
1. Open the project in Android Studio.
2. Sync Gradle.
3. Run on an Android phone.
4. Allow camera permission.
5. Build > Build Bundle(s) / APK(s) > Build APK(s).

GitHub:
The .github/workflows/build-apk.yml workflow builds a debug APK. In GitHub open Actions > Build Android APK > Run workflow, then download the artifact.
