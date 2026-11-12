# MarketPos (Android, Java)

Starter project that follows the MarketPos mobile screens:

- **MainActivity**: dark home grid with 8 tiles (المشتريات، المبيعات، الموردين، العملاء، المصروفات، الصندوق، الاستعلامات، المخزون). Only الاستعلامات opens a screen for now; the rest show a "قريباً" message.
- **ReportsActivity**: date range at the top, then grouped report rows with red section headers, switch rows and a payment-method radio row.

## Build and run

1. Install Android Studio (it includes the Android SDK).
2. File > Open, choose this `MarketPos-Android` folder, and let Gradle sync. Android Studio creates the Gradle wrapper automatically if it is missing.
3. Run on a phone with USB debugging on, or on an emulator (Android 8.0 / API 26 or newer).

## Notes

- Layout direction is forced to RTL, so the Arabic UI reads right to left.
- Tile icons are emoji placeholders; replace them with real drawables later.
- Report rows and switches are not connected to data yet. Next steps: a SQLite database (Room or raw SQLite), then the real reports.

## Google Drive backup and reports

The app uploads files to the user's Google Drive with the `drive.file` scope, so it can only see files it creates.

One-time setup in Google Cloud Console (needed before sign-in works):

1. Create a project and enable **Google Drive API**.
2. Configure the **OAuth consent screen** (External, add your Google account as a test user).
3. Create an **OAuth client ID** of type **Android**, with package name `com.marketpos.app` and the SHA-1 of your signing key. For the debug key:

   ```
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```

Usage:

- **النسخ الاحتياطي** tile: sign in, then **نسخ احتياطي الآن** uploads the local database (`marketpos.db`). The database is created once the data layer is added.
- Cloud icon ☁ on each report row: saves that report as a CSV file to Drive. Right now the CSV has only the header and date range, because the reports have no data yet.

If the Drive API library versions fail to resolve, update the versions in `app/build.gradle`.

## Data (phase 2)

- **المبيعات** (SaleActivity): build an invoice from lines (item, quantity, price), choose cash, card or cheque, and save it to `marketpos.db`.
- **تقرير بالمبيعات**: tap the row to see the invoice count and total. The cloud icon saves the real invoice rows as CSV to Drive.
- **النسخ الاحتياطي** uploads `marketpos.db`, so it now contains real data.
- The other reports still have no data; their CSV has only the header.
