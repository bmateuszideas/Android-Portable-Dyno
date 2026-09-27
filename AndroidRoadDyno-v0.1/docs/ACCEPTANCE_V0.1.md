# v0.1 acceptance against the product PRD

This document tracks the first-stage GNSS logger. It does not certify the app as a dyno or establish the Galaxy S25 sample rate.

| PRD requirement | Implementation | Verification still needed |
| --- | --- | --- |
| Direct GNSS speed and monotonic fix time | `PhoneGnssSpeedSource`, `LocationMapper` | Confirm fixes on Galaxy S25 |
| Reported speed accuracy and real sample rate | `SpeedSample`, `SamplingStatisticsCalculator` | Inspect stationary and moving sessions |
| START/STOP with screen off | `MeasurementService` location foreground service and notification STOP | Screen off/Home test on device |
| Preserve raw sample order and metadata | Room `speed_samples` with `sampleIndex`, timestamp, provider, and anomaly flags | Inspect exported CSV |
| Persist completed and interrupted sessions | Room `measurement_sessions` and startup recovery | Force-stop/process interruption test |
| Export raw CSV | `CsvExporter` with system document picker | Open resulting CSV on another device |
| Replay same samples in recorded order | `ReplaySpeedSource` in realtime and maximum-speed modes | Run unit tests and replay a real session |

The database stores finite display copies of speed and speed accuracy alongside their original IEEE-754 bits. Replay and CSV reconstruct the original values from those bits, including `NaN` or infinity if Android supplies them. Derived power and filtering must be stored separately from these samples.

## Galaxy S25 field gate

1. Record 2–5 minutes while stationary. Export CSV and inspect fix-time intervals, speed noise, speed accuracy, missing speed flags, duplicate timestamps, and gaps.
2. Record a quiet 0–50 km/h drive and a constant-speed section. Compare the rate and reported accuracy with the stationary session.
3. Record one controlled acceleration on a closed test route. Inspect the shape of `speed(t)` and repeat the run for a first repeatability check.
4. Turn the screen off and back on during a session. Stop from the notification, then verify that Room sample count equals the CSV row count and replay count.
5. Interrupt one recording, reopen the app, and confirm that already committed samples remain available as `INTERRUPTED`.

The next milestone, offline v0.2 power calculations, can be tuned against those recorded files. The PRD's mass, RPM, coastdown, torque, vehicle profiles, and run comparisons belong to later milestones.
