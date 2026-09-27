# RPM and tyre calibration implementation

User request: two consecutive GPS capture buttons at 2000 and 3000 RPM on the measurement gear; tyre size and mathematical wheel/travel diagnostics, inside the same application.

- RpmCalibration fits RPM = k * km/h through zero using both captures. Saves both observations and reports their relative ratio disagreement without an arbitrary rejection threshold. Invalid, zero, non-finite or reversed speeds are rejected. Capture requires a GNSS fix no older than 2.5 seconds and a new timestamp for the second step.
- RpmCalibrationDialog uses a lifecycle-controlled view model and maximum-rate GPS_PROVIDER source only while resumed. No measurement session is created. Step 1 advances to step 2 automatically; step 2 saves for vehicle name + gear and returns to the setup form. Cancellation leaves previous calibration intact. Changing vehicle or gear clears the currently applied calibration; saved matches can be explicitly loaded.
- TyreGeometry accepts markings such as 225/45 R17; D = 25.4 * rim + 2 * width * profile / 100 mm, C = pi * D. This is nominal unloaded geometry, not an observed rolling circumference. wheelRPM = v / C * 60; distance = integral v dt; turns = distance / C. It cannot independently validate its GNSS input.
- WheelTravelCalculator supplies live and replayable session diagnostics. Trapezoidal integration skips non-monotonic timestamps and invalid speeds and does not bridge gaps longer than 5 seconds; incomplete coverage is shown. Raw samples are unaffected.
- Room migration 2→3 appends two captured speeds and tyre size. Intent extras, defaults, completed-session editing and CSV exports carry these fields. Raw CSV import restores configuration when available.
- Test known calibration, disagreement, invalid and stale capture, tyre dimensions, wheel RPM, exact constant/linear speed distance, tyre independence of distance, gaps/duplicates, actual engine RPM, export and import metadata.
- CI runs testDebugUnitTest and assembleDebug. Device acceptance: permission, 2000 capture, automatic next step, 3000 capture, rotation/background, gear change/reload, live wheel/travel and persisted session/CSV. Device checks remain for physical S25.

The distributed development APK will use a locally retained signing key. No signing key or password-dependent production credential is committed to GitHub. Previous ephemeral debug builds cannot be updated in place without their lost key.
