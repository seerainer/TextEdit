TextEdit
=======

A small Java-based text editor built with SWT and Gradle. This repository contains the source for the TextEdit application, unit and integration tests, and Gradle build configuration (including a wrapper) so you can build, run and package the app locally.

Key points
- Java target: Java 25
- UI toolkit: SWT (Standard Widget Toolkit)
- Build system: Gradle (use the bundled Gradle wrapper)

Requirements
- JDK 25 installed and available on your PATH
- Git (optional, for cloning and source control)

Quick start
- On Unix/macOS:

  ```bash
  ./gradlew run
  ```

- On Windows (PowerShell / cmd):

  ```powershell
  gradlew.bat run
  ```

Build
- Build, run tests, and assemble artifacts:

  ```bash
  ./gradlew build
  ```

- Clean build artifacts:

  ```bash
  ./gradlew clean
  ```

Run
- Run the application locally:

  ```bash
  ./gradlew run
  ```

Testing
- The project uses JUnit 5 and AssertJ for tests.
- Run all tests:

  ```bash
  ./gradlew test
  ```

- Run only unit tests or integration tests:

  ```bash
  ./gradlew unitTest
  ./gradlew integrationTest
  ```

- Run a single test class or method (example):

  ```bash
  ./gradlew test --tests "io.github.seerainer.textedit.UndoManagerTest"
  ./gradlew test --tests "io.github.seerainer.textedit.UndoManagerTest.testPush"
  ```

Native image
- Build a native executable (GraalVM native image required):

  ```bash
  ./gradlew nativeCompile
  ```

Troubleshooting
- If you see SWT errors, ensure you're running a compatible JDK and have the appropriate SWT native libraries for your platform.
- If Gradle or the wrapper fails, verify `JAVA_HOME` points to a Java 25 JDK.
