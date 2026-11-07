# OffTheGrid

An Android app where nearby phones chat over local Wi-Fi with no internet and no central server. Phones find each other with Network Service Discovery and exchange text and photos directly over TCP sockets.

## Features

**Nearby discovery**
- The Nearby tab lists phones on the same Wi-Fi network or hotspot as they appear, with their profile photos and a friend badge
- Phones are found automatically with Network Service Discovery; no IP addresses to type
- A presence monitor pings every known peer every five seconds, and a peer that misses two pings is removed from the list
- A Refresh button restarts discovery, re-fetches profile photos and pings every peer, with a spinning, dimmed button while it works
- A warning banner appears when the phone is not on Wi-Fi
- Profile photos update quickly: the avatar version travels with each presence reply, and a changed version triggers an immediate fetch

**Messaging**
- Text and photo messages sent directly over TCP, one JSON object per line
- Messages are saved first as Waiting to send and move to Delivered once the other phone acknowledges them; unsent messages retry automatically when the peer comes back
- The last sent message shows its status; there are no read receipts
- Reply to a message with a quoted preview
- Heart reactions by double-tapping a message or photo or from the long-press menu; a count shows only when more than one person reacted
- Long-press menu to reply, copy, save a photo to the gallery, heart or remove a heart, and delete
- Typing indicator, shown in the chat and in the chat list
- WhatsApp-style time on every bubble and date separators (Today, Yesterday, full date)
- The chat jumps to the newest message only when a new one arrives, so reacting to an old message does not scroll you away
- Chats are kept in SQLite, so history survives restarts and is shared through content providers

**Chats tab**
- One row per conversation with the last message, time, unread count and online or offline status
- Pin chats to the top, mute chats, and delete a whole conversation from the chat menu
- Friend requests appear as their own rows with the sender's photo and Accept and Decline buttons
- Empty states explain what to do when there are no chats or no one nearby

**Friends**
- Send, accept and decline friend requests; a request moves through pending-out and pending-in states on the two phones
- Unfriending is mirrored on the other phone, and the presence reply carries a friend flag so a mismatch is fixed after three disagreeing replies
- A Friends screen lists friends and pending requests, with online dots
- A friend's profile and posts are cached, so they stay viewable when the friend is offline

**Profile and posts**
- Profile with cover photo, avatar, display name, bio and counters for posts and friends
- Edit the profile with name, bio, cover photo and avatar from the camera, gallery or the avatar generator; cover and avatar can be cropped by dragging and pinching
- Create posts with text and an optional photo, set to Everyone or Friends only
- Edit a post (marked "edited"), delete a post, and view a photo post in an enlarged dialog
- Hearts on posts, with the owner seeing the count when more than one; hearts and edits stay linked to the post
- Viewing someone else's profile shows their posts, a Message button for friends or Add friend for others, and a refresh button
- Strangers' profiles and the enlarged post dialog block screenshots and screen recording; friends' profiles are not blocked

**Avatar generator**
- Generate avatars from the DiceBear REST API with Volley, with selectable styles and colours, a Shuffle button, and a grid of results
- One shared request queue, a timeout, no retries and cancellation when the screen closes
- An in-memory bitmap cache for avatars received from other phones, with retry timers for failed fetches

**Notifications and background service**
- A foreground listening service keeps the phone discoverable and receives messages while the app is closed
- The notification text follows the connection: it says the app is visible on Wi-Fi, or waiting for Wi-Fi when it is lost
- A Stay visible switch turns the background service on and off
- Separate notification channels for messages, friend requests and the service; muted chats stay silent; tapping a notification opens the right chat
- A banner warns when notifications are turned off for the app

**System events**
- A dynamically registered broadcast receiver reacts to airplane mode on and off, battery low and OK, and power saver on and off
- Toasts explain what each event means for nearby chat, and discovery restarts after airplane mode ends

**Account and cloud**
- Sign up and log in with Firebase Authentication using email and password
- Password rules and a strength bar (weak, okay, strong), a show and hide password toggle, and Forgot password by email
- Back up to Cloud Firestore: friends, hearts, posts, chats, messages, replies and images under the signed-in account, with a progress dialog
- Restore on a new phone, which shows progress and applies the saved data
- Firestore rules let each account read and write only its own backup
- Realtime Database presence: while the service runs, the phone writes to `/online/<deviceId>` and removes it on disconnect; a toast shows how many devices are online

**XML export**
- Export any chat from the chat menu to `chat_<name>.xml` using XmlSerializer
- The file is read back with XmlPullParser to show the message counts (sent and received) and the saved path

**Appearance**
- Light and dark themes, rounded dialogs, Polaroid-style photo posts and avatar chips

## Requirements

- Android Studio with a device or emulator on Android 7.0 or later (minSdk 24, target 33)
- Two phones on the same Wi-Fi network or hotspot to test chatting
- A Firebase project with Email/Password sign-in, Firestore and Realtime Database enabled
- Your own `google-services.json` in `app/`
- Internet only for login, backup, the online-devices count and avatar generation; chatting, friends and posts work offline

Dependencies (managed by Gradle): material 1.8.0, recyclerview 1.2.1, Firebase BoM 32.2.3 (auth, firestore, database), Volley 1.2.1.

Firestore rules:

```
match /backups/{uid}/{document=**} {
  allow read, write: if request.auth != null && request.auth.uid == uid;
}
```

Run from Android Studio with Run > Run 'app' on each phone.

## Project Layout

```
app/src/main/java/com/example/localchat/
├── Screens
│   ├── MainActivity / ChatsFragment / DiscoverFragment / ProfileFragment
│   ├── ChatActivity.java          # One conversation, export to XML
│   ├── FriendsActivity.java       # Friends and requests
│   ├── OtherProfileActivity.java  # A peer's profile and posts
│   ├── AuthActivity.java          # Sign up, login, reset password
│   └── AvatarGenActivity.java     # DiceBear avatar picker
├── Adapters
│   ├── MessageAdapter.java
│   └── ConversationAdapter.java
├── Background
│   ├── ListenerService.java       # Accept loop, NSD registration, notification
│   ├── PresenceMonitor.java       # Pings peers, friend flag reconcile
│   └── SystemEventReceiver.java   # Airplane, battery, power saver
├── Network
│   ├── NsdHelper.java             # Discovery and registration
│   ├── PeerClient.java            # Outgoing TCP
│   ├── Wire.java                  # JSON-line packets
│   ├── PeerRegistry.java          # Current nearby peers
│   └── MessageSink.java
├── Data
│   ├── Message and post storage   # SQLite + content providers
│   ├── PostStore.java
│   ├── Profile.java / RemoteProfile.java / ProfileShare.java
│   └── PendingReactions.java
├── Cloud
│   ├── FirestoreBackup.java       # Backup and restore
│   ├── CloudPresence.java         # Realtime Database presence
│   ├── AvatarApi.java             # Volley request to DiceBear
│   └── AvatarCache.java
└── Export
    └── ChatXml.java               # XmlSerializer and XmlPullParser
```

## Concepts Used

Organized by the course topics.

**Android Components**

- Activities and fragments with a bottom navigation bar
- Started and foreground Service with a persistent notification and notification channels
- Dynamically registered BroadcastReceiver for system events (airplane mode, battery low/OK, power saver)
- Content providers that expose messages and posts to the rest of the app
- Runtime permissions (notifications on Android 13 and later) and a multicast lock

**UI and Lists**

- RecyclerView with adapters for messages, chats, friends, nearby peers and profile posts
- ConcatAdapter for the profile header plus the post list
- ViewBinding, ConstraintLayout and FrameLayout, dialogs, menus and day/night themes

**Networking**

- NSD (mDNS) service type `_offthegrid._tcp.` for peer discovery
- TCP sockets on port 5050 with one JSON object per line
- Volley ImageRequest to the DiceBear REST API, with an in-memory bitmap cache
- Worker threads for sockets and a handler to return results to the UI

**Local Storage**

- SQLite for messages, friends and posts
- SharedPreferences for profile and settings
- App-private files for avatars, covers and post photos

**Firebase**

- Authentication: email and password, password reset
- Cloud Firestore: per-account backup and restore under `backups/<uid>`, protected by security rules
- Realtime Database: `/online/<deviceId>` presence with `onDisconnect().removeValue()`
- Firebase Storage is not used; images are backed up as compressed data in Firestore

**XML**

- XmlSerializer writes a chat to `chat_<name>.xml`
- XmlPullParser reads it back and shows message counts and the file path

## Known Gaps

**Networking**
- Both phones must be on the same Wi-Fi network or hotspot. Nothing works across the internet or over mobile data.
- Some public or guest Wi-Fi networks block device-to-device traffic, so phones may not see each other there.
- A phone that goes out of range stays on the Nearby list for about ten seconds before it is removed.
- An unsent message waits on the sender and retries when the peer returns. It is not stored on any server.

**Security and privacy**
- Messages and photos are not encrypted on the network.
- Phones are trusted by display name and device id, so someone nearby could pretend to be another person.
- The Realtime Database runs in test mode for the demo and would need stricter rules in production.

**Identity and accounts**
- A phone is identified by a device id kept in the app. Reinstalling creates a new identity, and a restore copies the old data onto the new one.
- Two people with the same display name get a numeric suffix.
- Restoring a backup on a phone that already has chats overwrites them.
- There is no email verification and no account deletion inside the app.

**Features not built**
- No group chats and no message search.
- Messages support text and photos only, with no voice notes, videos or files.
- There are no read receipts, by design.

**Platform limits**
- Android always shows a notification for a foreground service, so it can be silenced but not hidden.
- Some phone makers stop background services aggressively, so battery optimisation may need to be turned off for the app.
- Avatar generation needs internet and depends on the DiceBear service.
- The app is designed for portrait phones and has been tested on a small number of devices.
