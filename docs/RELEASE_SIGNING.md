# Manga Inker release signing

The release keystore and passwords must not be committed to the repository. The Gradle build reads signing values only from environment variables, and GitHub Actions writes the keystore to a temporary runner file from repository secrets.

Configure these repository Actions secrets before expecting a signed release APK:

- `MANGA_INKER_RELEASE_KEYSTORE_BASE64`: Base64-encoded JKS file, with no line breaks.
- `MANGA_INKER_RELEASE_STORE_PASSWORD`: keystore password.
- `MANGA_INKER_RELEASE_KEY_ALIAS`: key alias.
- `MANGA_INKER_RELEASE_KEY_PASSWORD`: key password.

Example encoding commands on your own computer:

- Linux: `base64 -w 0 path/to/release.jks`
- macOS: `base64 path/to/release.jks | tr -d '\n'`

Do not paste keystore contents or passwords into chat, source files, workflow logs, or commits.

## If signing secrets are not configured

CI still runs unit tests and lint, builds the debug APK for physical-device testing, and builds an unsigned release variant. It does not publish an unsigned APK as an update. The private update-release job is skipped until protected signing is configured.

## Existing signing-key continuity

A release keystore was previously tracked in Git. Removing it from the current branch does not erase older Git history or invalidate copies already downloaded. Treat the old signing material as exposed to anyone who could read the repository. Before public distribution, decide whether to migrate the existing key into protected secrets or rotate to a new key.

A new signing key cannot update an installation signed by the old key using a normal APK install. Those installations would need an uninstall/reinstall, or a supported app-store signing migration. Do not rotate blindly if preserving installed-user updates matters.
