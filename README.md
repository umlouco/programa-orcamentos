# Programa Orçamentos

Offline Android app for a small construction company to create quotations, generate PDF files, share them, keep an archive, and export/import backups.

## Build

1. Open this folder in Android Studio.
2. Let Android Studio sync the Gradle project.
3. Run the `app` configuration on an Android device or emulator.
4. For delivery, create a signed APK with the same application id and signing key on every release.

The app uses Kotlin, Jetpack Compose, Room, DataStore, Android PDF APIs, Storage Access Framework, and FileProvider.

## Implemented Scope

- First-run company profile setup with logo selection.
- Editable company settings.
- Sequential budget numbers like `BUD-2026-0001`.
- Client details inside each budget.
- Editable line items with VAT-inclusive or VAT-exclusive price entry.
- Totals stored as integer cents.
- Archive with search, status filter, open, duplicate, and delete.
- Branded PDF generation with system share sheet.
- Manual backup export and import using Android document pickers.

## Delivery Notes

- Keep the signing key backed up. Losing it prevents future APKs from updating the installed app.
- Before installing a major update, export a `.budgetbackup` file from the app.
- The app stores data locally and does not require internet access or an online account.
