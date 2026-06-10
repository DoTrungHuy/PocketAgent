# Third-Party Notices

AgentPad Native Core uses open-source Android and Kotlin libraries. The release
SBOM contains the complete resolved dependency graph and license metadata.

Primary dependencies include:

- AndroidX Activity, Lifecycle, Navigation, DataStore, and Room (Apache-2.0)
- Jetpack Compose and Material 3 (Apache-2.0)
- Kotlin and kotlinx.coroutines (Apache-2.0)
- org.json Java implementation, used only by JVM tests (JSON license)
- CycloneDX Gradle Plugin, used only during builds (Apache-2.0)

The compatibility package under `termux-lite/` is distributed separately from
the native Android application. Its own documentation and packaged source list
the applicable components and licenses.

This notice is informational. The license text shipped by each dependency and
the CycloneDX SBOM remain authoritative.
