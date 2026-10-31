---
layout: null
title: "Privacy Policy — Century War"
permalink: /store/privacy-policy-en/
lang: en
---

# Century War Privacy Policy

**Effective date:** November 1, 2026  
**Applies to:** Century War for Android, including Google Play Games integration where it has been configured in the published release.

## 1. Publisher and contact

Century War is developed under the **WooGitsu Games** label. The publisher is responsible for the game's own features and the privacy disclosures presented to users on Google Play. Google processes data within its Google Play Games service under its own policies.

**Details to complete before publishing:**
- Publisher's full legal name: **[INSERT_PUBLISHER_LEGAL_NAME]**
- Privacy contact email: **[INSERT_CONTACT_EMAIL]**

You may use this address for questions about data practices and requests concerning data handled by the publisher. Data held in Google Play Games can also be deleted using Google's tools described below.

## 2. Data stored on your device

The game stores the following in its private app storage:
- your in-game commander nickname;
- campaign stages, stars, medals, research, missions, achievements, and local leaderboard records;
- statistics (such as battles, wins, defeated units, and play time);
- settings (such as language, graphics quality, sound, vibration, reminders, and reminder hour);
- a temporary interrupted-battle save for resuming play (the battle.snap file).

Core gameplay, local leaderboards, and local progress saving **work without an internet connection**. No publisher account is required. The app does not request access to location, contacts, microphone, or camera. There are no ads, in-app purchases, or chat features.

The battle.snap file is used only to resume a battle locally; it is not uploaded in the Play Games cloud save and is excluded from Android system backup rules.

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

**The cloud save contains the complete game profile in JSON format**, not just campaign progress: your in-game nickname, local scores (including the names and dates stored in score entries), progress, medals, research, missions, local achievements, statistics, and settings. The saved profile may be read after restarting the game or on another device authenticated to the same PGS account. If saves conflict, the game chooses the profile with greater progress under its own rules.

**Google Play Games achievements:** the current code provides local achievements, but transmitting unlocks to the Google achievements service is not yet connected. Local achievements are nevertheless part of the profile included in cloud saves.

The app does not currently submit this information to a publisher-operated leaderboard server. If such a service is activated, this policy will be updated before launch.

Google states that Play Games data is encrypted in transit using HTTPS. Information about data collection, retention, and profile visibility: https://developer.android.com/games/pgs/data-collection

## 5. Local Daily Challenge reminders

The game can remind you about the Daily Challenge once per day, **after your first win** and while reminders are enabled. The hour is adjustable in game Settings. On Android 13 or later, the app requests system notification permission after the first win, rather than immediately upon launch.

Reminders are scheduled locally using Android's AlarmManager and displayed using NotificationManager. They may be rescheduled after a device restart, app update, or clock change. **No data is sent to a server to deliver these reminders**, and the app does not use Firebase Cloud Messaging or other push notifications.

You can disable reminders in **game Settings** and independently through **Android notification settings**.

## 6. Android permissions

| Permission | Purpose |
|---|---|
| INTERNET | Connecting to Play Games when the service is configured; core gameplay still works without a connection |
| POST_NOTIFICATIONS | Local Daily Challenge reminders with user permission on Android 13+ |
| RECEIVE_BOOT_COMPLETED | Rescheduling local reminders following a device restart |
| VIBRATE | Game haptic feedback, which can be disabled in Settings |

INTERNET permission does not mean that every game feature requires a continuous connection.

## 7. Android system backup

Depending on your device and Google account settings, Android may back up app data. This game's rules restrict Android cloud backup and device-to-device transfer to **profile.json**; battle.snap is excluded. The publisher does not automatically gain access to Android's system backup.

Android backup is **separate** from Google Play Games Saved Games synchronization and may have different deletion and restoration rules. See https://developer.android.com/identity/data/autobackup and https://policies.google.com/privacy.

## 8. Deletion and retention

- **Local data:** the in-game “Reset progress” action clears progress and records but **retains your nickname**. To remove the nickname and remaining local data, clear the app's storage in Android Settings or uninstall the game. Android backup copies may remain available under your account settings.
- **Google Play Games data:** go to https://play.google.com/games/profile, open your Play Games data settings, and **delete the data for this specific game**. Alternatively: Play Store → profile → Settings → General → Play Games profile → Privacy and settings → Delete profile and game data. Menu names may change. You can also delete your full Play Games profile. Instructions: https://support.google.com/googleplay/answer/9130646?hl=en.
- **Important:** a local reset does not delete PGS data; later synchronization may restore progress from the cloud. Deleting Play Games data does not automatically delete the local profile or Android system backup.

Google's retention periods are determined by Google and your account settings. The publisher does not currently maintain a separate player-profile database on its own server.

## 9. Data protection and children

Local records are stored in Android's private app storage. The game has no chat, player-to-player messages, custom file sharing, or location sharing. PGS leaderboards may nevertheless show Google profile names and scores according to the selected visibility settings. The game's content rating comes from the IARC questionnaire and does not, by itself, mean that the product targets children.

## 10. Policy changes

Before adding new data practices (such as publisher-hosted online leaderboards, advertising, or purchases), we will update this policy and the Google Play disclosures.

_Polish version: /store/privacy-policy-pl/._
