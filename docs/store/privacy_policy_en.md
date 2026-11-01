---
layout: null
title: "Privacy Policy — Century War"
permalink: /store/privacy-policy-en/
lang: en
---

# Century War Privacy Policy

**Effective date:** {{OWNER.release_date}}  
**Applies to:** Century War for Android, including Google Play Games integration where it has been configured in the published release.

## 1. Publisher and contact

Century War is developed under the **WooGitsu Games** label. The publisher is responsible for the game's own features and the privacy disclosures presented to users on Google Play. Google processes data within its Google Play Games service under its own policies.

**Publisher details (source: [OWNER_INPUT.md](OWNER_INPUT.md)):**
- Publisher's legal name: **{{OWNER.publisher_name}}**
- Postal contact address: **{{OWNER.postal_address}}**
- Privacy and deletion contact email: **{{OWNER.contact_email}}**
- Published policy URL: **{{OWNER.privacy_policy_url}}**

You may use this address for questions about data practices and requests concerning data handled by the publisher. Data held in Google Play Games can also be deleted using Google's tools described below.

## 2. Data stored on your device

The game stores the following in its private app storage:
- your in-game commander nickname;
- campaign stages, stars, medals, research, missions, achievements, and local leaderboard records;
- statistics (such as battles, wins, defeated units, and play time);
- settings (such as language, graphics quality, sound, vibration, reminders, and reminder hour);
- a temporary interrupted-battle save for resuming play (the battle.snap file);
- up to 10 local crash and error reports (the files/crash directory);
- if you opt into the publisher's leaderboard: a random UUID and a separate 32-byte
  installation secret stored in `profile.json` to establish ownership of your scores.

Core gameplay, local leaderboards, and local progress saving **work without an internet connection**. No publisher account is required. The app does not request access to location, contacts, microphone, or camera. There are no ads, in-app purchases, or chat features.

The battle.snap file is used only to resume a battle locally; it is not uploaded in the Play Games cloud save and is excluded from Android system backup rules.

### Local problem reports

After a crash, the app stores a technical report on the device containing the game version, device model, Android version, current screen, the last 30 technical events (such as screen changes and battle stage), and the call stack. It does **not** include the in-game nickname, player IDs, exception messages, or profile data. Up to 10 reports are retained; older reports are automatically removed. These files are not uploaded to Play Games and are excluded from Android system backup.

On the next launch after a crash, the game asks **once** whether you want to share the report. You can also choose **Settings → About → Report a problem**. Both options only open the Android system text-sharing chooser (for example, email); the game **never automatically sends reports** to the publisher or a third-party service. You decide whether to send the report and to whom. Clearing the app's storage also deletes local reports.

## 3. Google Play Games — account and authentication

The game includes **Google Play Games Services v2** integration. It works only in builds for which the publisher has configured valid service identifiers. Google Play Games online features are not required to play without internet access.

When enabled, the Google SDK may **automatically authenticate your Play Games profile on game launch** if your account is configured for this. The app also checks authentication when returning to the foreground. The game's Settings screen contains a Play Games screen where you can retry sign-in or request synchronization. **Scores and the profile backup may be transferred automatically after authentication**, without a separate confirmation for each upload.

Authentication uses the Play Games account identity, including player ID and profile name. Google may also process your avatar and analytics or diagnostics for its services. The game does not require you to give the publisher your Google password, email address, or phone number.

**Signing out / disabling authentication:** the current app has no in-game Play Games sign-out button and no separate cloud-sync toggle. You can manage Play Games accounts and automatic sign-in for games in your Google Play or Android account settings; exact menus differ by device and services version. Switching accounts or disabling Play Games authentication **does not automatically delete** previously stored data.

Google's policy: https://policies.google.com/privacy  
Profile controls: https://play.google.com/games/profile  
Google Help: https://support.google.com/googleplay/answer/9130646?hl=en

## 4. Google Play Games leaderboards and cloud saves

When the service is configured and you are authenticated, the app can send scores to Google for **four leaderboards**: Endless Siege, Daily Challenge, campaign stars, and Skirmish. Google's public leaderboards may display your Play Games profile name and score according to your profile's visibility settings. Your *in-game* nickname is not submitted as the public name used by the PGS leaderboard UI.

**The cloud save contains the game profile in JSON format, excluding the local publisher-leaderboard UUID, installation secret, and consent flag**, not just campaign progress: your in-game nickname, local scores (including the names and dates stored in score entries), progress, medals, research, missions, local achievements, statistics, and settings. The saved profile may be read after restarting the game or on another device authenticated to the same PGS account. If saves conflict, the game chooses the profile with greater progress under its own rules.

**Google Play Games achievements:** the current code provides local achievements, but transmitting unlocks to the Google achievements service is not yet connected. Local achievements are nevertheless part of the profile included in cloud saves.

The game also supports an **optional publisher-operated leaderboard server**, enabled only in releases with a configured HTTPS endpoint, HMAC key and privacy policy URL, and after separate in-game consent. The app sends a random player UUID, nickname, board, score, timestamp, battle settings, replay commands for score verification, and a **32-byte installation secret** for requests concerning your own account. The server verifies the secret against a SHA-256 digest bound to the UUID: the secret is transmitted over HTTPS but **not stored in clear text in SQLite**. SQLite records the UUID, nickname, results, verification metadata, a SHA-256 submission hash, and a **pseudonymous public ID** derived via HMAC from the UUID with a separate server-only key. Public leaderboards show nickname, result and public ID, **not the raw UUID**. SQLite does not retain the complete replay or IP addresses. IP addresses are temporarily used for rate limiting and may appear in infrastructure logs. Without configuration and consent, no scores are sent to the publisher server. The code alone does not imply a live deployment.

**Nickname reports:** long-pressing a publisher leaderboard entry lets you report a nickname for moderation. The publisher receives the reported player's public ID and reason ("offensive"); the server also stores that player's UUID, publicly displayed nickname at report time, and timestamp. The reporter does not need to provide an account. The reporter's IP is temporarily used for rate limiting and may be present in proxy logs. Reports are accessible only to the administrator for abuse moderation, not advertising.

**Public nicknames and reporting abuse:** if the publisher's leaderboard server is enabled, other players may see the nickname you choose and your scores. The public entry identifier is not your internal installation UUID; an installation secret is used to authenticate score submissions and deletion requests. The server may store a hash of that secret, not its plaintext. Once the nickname-reporting feature from task 80 has been integrated, players can long-press a leaderboard entry to report an abusive name. A report contains the target's public entry ID and a reason; moderators can review reports and take action. The reporter's IP address may be used for request rate limiting or recorded in infrastructure logs according to the server configuration. These features apply only after the relevant app and server release is actually deployed.

Google states that Play Games data is encrypted in transit using HTTPS. Information about data collection, retention, and profile visibility: https://developer.android.com/games/pgs/data-collection

## 5. Local Daily Challenge reminders

The game can remind you about the Daily Challenge once per day, **after your first win** and while reminders are enabled. The hour is adjustable in game Settings. On Android 13 or later, the app requests system notification permission after the first win, rather than immediately upon launch.

Reminders are scheduled locally using Android's AlarmManager and displayed using NotificationManager. They may be rescheduled after a device restart, app update, or clock change. **No data is sent to a server to deliver these reminders**, and the app does not use Firebase Cloud Messaging or other push notifications.

You can disable reminders in **game Settings** and independently through **Android notification settings**.

## 6. Android permissions

| Permission | Purpose |
|---|---|
| INTERNET | Connecting to Play Games and the optional publisher-hosted HTTPS leaderboard; offline play still works |
| POST_NOTIFICATIONS | Local Daily Challenge reminders with user permission on Android 13+ |
| RECEIVE_BOOT_COMPLETED | Rescheduling local reminders following a device restart |
| VIBRATE | Game haptic feedback, which can be disabled in Settings |

INTERNET permission does not mean that every game feature requires a continuous connection.

## 7. Android system backup

Depending on your device and Google account settings, Android may back up app data. This game's rules restrict Android cloud backup and device-to-device transfer to **profile.json**; battle.snap is excluded. The publisher does not automatically gain access to Android's system backup.

Android backup is **separate** from Google Play Games Saved Games synchronization and may have different deletion and restoration rules. See https://developer.android.com/identity/data/autobackup and https://policies.google.com/privacy.

## 8. Deletion and retention

- **Local data:** the in-game “Reset progress” action clears progress and records but **retains your nickname**. To remove the nickname and remaining local data, clear the app's storage in Android Settings or uninstall the game. Android backup copies may remain available under your account settings.
- **Publisher-hosted leaderboard data and account-related nickname reports:** select **Settings → New identity → Confirm**. The app persistently queues a request to delete the old UUID's scores from every board and its nickname from the server, then generates a new UUID. Offline requests are retried on later launches, even if uploading new scores is disabled. **A new identifier does not confirm server-side deletion.** The old records may remain visible until the request is delivered; do not uninstall the game or clear app storage before delivery. Contact the publisher using the privacy address in section 1 if necessary. Publisher leaderboard scores and reports tied to the UUID remain until a deletion request or the service ends; they do not automatically expire. Deleting an identity also removes reports attached to its UUID, but an internal record of a blocked UUID may be retained to prevent repeated abuse. Reporting another player's nickname does not link the report to your own UUID, so changing your identity does not delete reports about other players. Local backups are rotated and deletions after a backup must be re-applied if it is restored.
- **Google Play Games data:** go to https://play.google.com/games/profile, open your Play Games data settings, and **delete the data for this specific game**. Alternatively: Play Store → profile → Settings → General → Play Games profile → Privacy and settings → Delete profile and game data. Menu names may change. You can also delete your full Play Games profile. Instructions: https://support.google.com/googleplay/answer/9130646?hl=en.
- **Important:** publisher leaderboard records, Play Games data, local records and Android system backup are four separate data stores. Local reset does not delete publisher-hosted scores or PGS data; PGS synchronization may restore progress.

Google's retention periods are determined by Google and your account settings. The publisher may hold the pseudonymous leaderboard data described above if its optional server is enabled.

## 9. Data protection and children

Local records are stored in Android's private app storage. When leaderboards are enabled, player profile names and scores may be visible publicly on PGS and the publisher-operated server. Players cannot publish posts or send direct messages. The abusive-name reporting and moderation mechanism becomes available once task 80 has been integrated and deployed. The game has no chat, player-to-player messages, or location sharing. Technical reports can only be shared manually through Android's system chooser. PGS leaderboards may nevertheless show Google profile names and scores according to the selected visibility settings. The game's content rating comes from the IARC questionnaire and does not, by itself, mean that the product targets children.

## 10. Policy changes

Before activating the publisher-hosted leaderboard in a published release or adding new data practices (such as advertising or purchases), we will verify this policy and the Google Play disclosures.

_Polish version: /store/privacy-policy-pl/._
