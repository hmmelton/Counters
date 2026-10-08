# Counters

A small Jetpack Compose app that shows a scrolling list of counters. Each counter starts at `0` and begins counting up once per second the first time its row appears on screen.

The requirements are simple, but they touch most of the subtle parts of Compose performance: state that has to survive `LazyColumn` disposing rows, timers that must stay accurate, visibility detection, and keeping recomposition to the rows that actually changed.

<!-- Optional: add a screen recording -->
<!-- <p align="center"><img src="docs/demo.gif" width="300" alt="Counters scrolling demo" /></p> -->

## Requirements

- A counter shows `0` until its row first becomes visible.
- Once started, it increments every second, exactly on its own second boundaries (`startTime + 1s`, `+ 2s`, …).
- Scrolling a row away and back shows the correct elapsed count immediately.

## How it works

```
LazyColumn ──visible items (snapshotFlow)──▶ MainViewModel.onItemsFirstVisible()
    ▲                                              │
    │                                     sets startTime once per counter
    │                                              ▼
 CounterCell ◀── StateFlow<List<Counter>> ◀── PersistentList<Counter>
    │
 ActiveSecondsText ◀── rememberElapsedSeconds(counter): IntState
                         ticks on the counter's own second boundaries
```

### Store when a counter started, not its count

```kotlin
data class Counter(val id: Int, val startTime: Long? = null) {
    fun elapsedSecondsAt(now: Long): Int =
        startTime?.let { ((now - it) / 1000).toInt() } ?: 0

    fun millisUntilNextTickAt(now: Long): Long? =
        startTime?.let { 1000 - (now - it) % 1000 }
}
```

`LazyColumn` discards a row's composition, including anything `remember`ed, when the row scrolls out of view. A running count stored in the row would be lost. Instead, the ViewModel stores a start timestamp per item, and the displayed value is always derived from it.

An off-screen counter is a single `Long`. When its row comes back, the value is correct on the first frame.

The timing rules live on the model as pure functions of `now`, so they have no dependency on Compose or the system clock and can be unit-tested directly. `millisUntilNextTickAt` returns `null` until the counter has started, because a counter that isn't running has no next tick. Member functions don't affect a data class's `equals`, `hashCode`, `copy`, or how the Compose compiler judges its stability.

### Detect visibility once per change, in batches

The screen observes `LazyListState.layoutInfo.visibleItemsInfo` with `snapshotFlow` and reports newly visible items to the ViewModel in batches. `distinctUntilChanged()` makes sure the ViewModel only hears about changes to the *set* of visible items, not every scroll frame.

`LaunchedEffect` in each row would not work for this: `LazyColumn` prefetches rows just past the viewport, so their timers would start before they are visible.

### Tick on each counter's own second boundary

```kotlin
@Composable
fun rememberElapsedSeconds(
    counter: Counter,
    clock: () -> Long = System::currentTimeMillis,
): IntState {
    val startTime = counter.startTime
    val seconds = remember(startTime) {
        mutableIntStateOf(counter.elapsedSecondsAt(clock()))
    }
    if (startTime != null) {
        LaunchedEffect(startTime) {
            while (true) {
                val now = clock()
                seconds.intValue = counter.elapsedSecondsAt(now)
                delay(counter.millisUntilNextTickAt(now) ?: return@LaunchedEffect)
            }
        }
    }
    return seconds
}
```

- **Read the clock, don't increment.** `activeSeconds++` with `delay(1000)` drifts, because every `delay` resumes slightly late and that lateness accumulates. It also permanently loses seconds when the main thread stalls. Recomputing from the clock corrects both on the next tick.
- **Sleep only until the next boundary.** A row that returns 4,370ms after it started waits 630ms, not 1,000ms, so it stays in phase with its original start, and with any counters that started alongside it.
- **Seed the initial value synchronously.** `remember(startTime) { … }` computes the current count during composition, so a returning row never shows `0` for a frame before the effect runs.
- **Key on `startTime`.** The effect and the seeded state belong to a specific start time, so they reset correctly if it changes.
- **Return `IntState`, not `State<Int>`.** `IntState` has its own `by` delegate that reads `intValue` directly, so neither writes nor reads box the `Int`.
- **Keep the coroutine tied to composition.** The tick loop is a `LaunchedEffect`, so it is cancelled automatically when the row leaves the screen. Only the timing rules are extracted; the lifecycle stays with the UI that needs it.
- **Read the state as low as possible.** `ActiveSecondsText` reads `rememberElapsedSeconds(counter)` and contains only a `Text`, so each tick recomposes that `Text` and nothing else in the row.
- **The clock is injectable.** The `clock` parameter lets tests drive time with a virtual clock instead of waiting in real time.

### Update one item without copying the list

The ViewModel's state is a `PersistentList` from [kotlinx.collections.immutable](https://github.com/Kotlin/kotlinx.collections.immutable). Setting a `startTime` with `mutate { … }` produces a new list that shares almost all of its structure with the old one, instead of an O(n) copy from `map`.

Structural sharing also means unchanged rows keep the **same `Counter` instance**, which is what makes skipping cheap.

### Maximize skipping without paying for `equals()`

| Check | What happens | Cost |
|---|---|---|
| `StateFlow` emission | `equals()` stops at the first changed element; a no-op update returns the same instance | O(index), O(1) for a no-op |
| `MainScreen(counters: List<Counter>)` | The parameter is typed as plain `List`, so strong skipping compares with `===` | O(1) |
| `CounterCell(counter)` for each **composed** row | Unchanged rows receive the same instance, so the data-class `equals` returns at `this === other` | O(1) per visible row |

The composable parameter is deliberately `List<Counter>`, not `ImmutableList<Counter>`. A stable collection type makes the skip check call `equals()`, which walks the list. `MainScreen` only recomposes *because* the list changed, so that walk would always end in "different". `===` reaches the same answer in constant time.

The result: when a counter starts, only the row that changed recomposes. Every other visible row skips, and off-screen rows aren't involved at all.

## Project structure

```
app/src/main/java/com/harrison/counters/
├── MainActivity.kt          # Hosts the Compose UI and obtains the ViewModel
├── data/
│   └── Counter.kt           # Immutable model (id + optional start time) and its timing rules
└── ui/
    ├── MainViewModel.kt     # Owns StateFlow<List<Counter>>, starts counters
    ├── MainScreen.kt        # LazyColumn, visibility tracking, CounterCell, rememberElapsedSeconds
    └── theme/               # Material 3 theme
```

## Tech stack

- Kotlin 2.4
- Jetpack Compose (BOM 2026.09.00) with Material 3
- AndroidX Lifecycle 2.11: `ViewModel`, `collectAsStateWithLifecycle`
- Kotlin Coroutines and `StateFlow`
- kotlinx.collections.immutable 0.5.2
- Android Gradle Plugin 9.1, Gradle 9.3
- minSdk 31, targetSdk 37

## Getting started

1. Clone the repository:
   ```bash
   git clone https://github.com/hmmelton/counters.git
   ```
2. Open the project in a recent version of Android Studio that supports AGP 9.1. Gradle provisions JDK 21 automatically through the daemon toolchain.
3. Run the `app` configuration on a device or emulator running Android 12 (API 31) or later.

Or from the command line:

```bash
./gradlew installDebug
```

## Inspecting Compose compiler reports

The build is configured to write Compose compiler metrics and reports, which show how the compiler classifies each class and composable parameter (stable, unstable, skippable):

```bash
./gradlew clean assembleRelease
```

Reports are written to `app/build/compose_metrics/`. Do a clean build first: an incremental build only reports the files it recompiled.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
