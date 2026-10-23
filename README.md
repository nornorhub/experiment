# nornorhub apps

Small Android apps. Every push runs the **Build APK** GitHub Actions workflow, which runs the
unit tests, builds the apps, and publishes the APKs on the repository's **Releases** page.
Download an APK on your phone and open it to install (allow "install unknown apps" when asked).

## 🇺🇸 FedFinder (`fedfinder/`)

Searches every job on [USAJOBS](https://www.usajobs.gov) (there's no official app) using the official
USAJOBS Search API.

- Keyword, location + distance, remote only, GS grade range, minimum salary, full/part-time, who can apply
- **🎓 Internships** (students and Pathways interns) and **recent graduates** filters
- Sort by best match, newest, closing soonest or highest pay
- Job cards with plain-English pay (hourly pay also shown per year), grade and a deadline countdown
- Full listings with **Apply on USAJOBS**, Save and Share
- ⭐ Saved jobs, sorted by deadline
- 🔔 Saved searches that check for new jobs every few hours and notify you

Needs a free USAJOBS API key: request one at <https://developer.usajobs.gov/apirequest/> and enter it
with your email in the app. Not affiliated with USAJOBS or OPM; you always apply on usajobs.gov.

## 👻🚌 Ghost Bus (`ghostbus/`)

An NJ Transit bus tracker for when the official app's times can't be trusted.

- **Every stop and route**: search by stop name or the number on the bus-stop sign, find stops near you,
  or browse all routes.
- **Live arrivals with a trust level** for each bus:
  - 📍 GPS updated recently, with how far away the bus is
  - ⚠️ GPS lost: the bus stopped reporting a few minutes ago
  - 👻 No live GPS: NJ Transit predicts the trip but no bus is reporting, so it often never shows up
  - ❌ Canceled
- **Map** of one bus, or every bus on a route, plus how many predicted trips have no bus behind them.
- **Your own log**: tap "I caught this bus" or "This bus never came" and each stop shows how late
  that route usually runs there and how often it no-shows.
- Service alerts for the routes at your stop.

### Setup

Ghost Bus uses NJ Transit's official live data (GTFS and GTFS-realtime), which needs a free developer
account: register at <https://developer.njtransit.com>, then sign in in the app. The login is stored
only on the phone. On first sign-in the app downloads every stop, route and trip (this can take a minute);
use **⋮ → Update stops & routes** when NJ Transit changes schedules.

The live feeds come from NJ Transit, so predictions are only as good as their data; Ghost Bus's job is to
show you how much to trust each one. NJ Transit's data terms apply; this is meant for personal use.

### Tests

`./gradlew :ghostbus:testDebugUnitTest` checks the GTFS-realtime decoder and arrival logic against a feed
generated with Google's official GTFS-realtime bindings (`ghostbus/src/test/fixtures/make_feed.py`).

## Tip Calculator (`app/`)

Enter a bill, pick a tip percentage and how many people are splitting it.

## Building locally

Requires the Android SDK (set `ANDROID_HOME`) and JDK 17+:

```sh
./gradlew assembleDebug
```
