# Gradle Setup (No Global Install Required)

This project uses the **Gradle Wrapper**. You do **not** need to install Gradle on your machine. The wrapper downloads the correct Gradle version on first run.

**Java version:** Use JDK 17 or 21. Gradle 8.5 does not support Java 22+. If you have Java 25 (or another newer JDK) as default, set `JAVA_HOME` to a JDK 17 or 21 (e.g. `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`) before running `./gradlew`.

## Option A: One-Time Command to Create the Wrapper (Recommended)

If you can run Gradle once (e.g. from another project or a one-off install), use it only to generate the wrapper JAR:

**macOS (Homebrew):**
```bash
brew install gradle
cd /path/to/Enhancing-Kademlia
gradle wrapper --gradle-version=8.5
```
Then use `./gradlew` for all builds. You can uninstall Gradle afterward: `brew uninstall gradle`.

**macOS / Linux (SDKMAN):**
```bash
sdk install gradle
cd /path/to/Enhancing-Kademlia
gradle wrapper --gradle-version=8.5
```

**Any OS (official installer):**  
Follow [Gradle’s install guide](https://gradle.org/install/), then in this project run:
```bash
gradle wrapper --gradle-version=8.5
```

After `gradle wrapper` has been run once, everyone (and CI) can use only `./gradlew`; no Gradle install is required.

---

## Option B: Download the Wrapper JAR Manually

If you prefer not to install Gradle at all, add the wrapper JAR by hand:

1. Ensure `gradle/wrapper/gradle-wrapper.properties` exists (it does in this repo).
2. Download the wrapper JAR into `gradle/wrapper/`:

```bash
cd /path/to/Enhancing-Kademlia
curl -L -o gradle/wrapper/gradle-wrapper.jar \
  https://github.com/gradle/gradle/raw/v8.5.0/gradle/wrapper/gradle-wrapper.jar
```

3. Make the script executable (if needed):

```bash
chmod +x gradlew
```

4. Run the build:

```bash
./gradlew build
```

The first run will download the Gradle 8.5 distribution (from `gradle-wrapper.properties`); later runs use the cached copy.

---

## After Setup

- **Build:**   `./gradlew build`
- **Tests:**   `./gradlew test`
- **Run app:** `./gradlew run --args="--port=8468"`
- **Help:**    `./gradlew run --args="--help"`

**Requirements:** JDK 17–21 (21 recommended for virtual threads). Gradle 8.5 does not support Java 22+. If you have multiple JDKs, set `JAVA_HOME` to a JDK 17 or 21 before running `./gradlew`.
