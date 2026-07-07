# AdGuard Home Quick Toggle

A minimal Android application that provides a Quick Settings tile to toggle your AdGuard Home protection on and off.

![App Screenshot](screenshots/screenshot.png)

## Features

- **Quick Settings Tile:** Toggle protection directly from your notification shade.
- **Status Updates:** The tile updates to reflect the current status of your AdGuard Home instance.
- **Notifications:** Optional notifications when protection is toggled.
- **Open Source:** Privacy-focused and transparent.

## Setup

1. **Prerequisites:**
   - [Android Studio](https://developer.android.com/studio)
   - An active AdGuard Home instance with API access.

2. **Configuration:**
   - Open the project in Android Studio.
   - Create a `.env` file in the root directory (use `.env.example` as a template) and provide your AdGuard Home credentials.
   - Alternatively, configure the credentials within the app UI upon first launch.

3. **Building for Release:**
   - To build your own signed APK without sharing credentials:
     1. Create a `keystore.properties` file in the root directory.
     2. Follow the template in `keystore.properties.example`.
     3. Run `./gradlew assembleRelease`.
   - The signed APK will be located in `app/build/outputs/apk/release/`.

## Contributing

Contributions are welcome! Feel free to open issues or submit pull requests to the [GitHub repository](https://github.com/harsha20599/Adguard-Quick-Toggle).

## License

This project is open-source and available under the [MIT License](LICENSE).
