# Agentic Development Guide

This repository contains a Java-based text editor application using SWT and Gradle. Follow these guidelines when developing or maintaining code in this repository.

## 1. Build, Run, and Test Commands

Use the provided Gradle wrapper (`./gradlew` on Unix-like, `gradlew.bat` on Windows) for all build tasks.

### Core Commands
- **Build Project:** `gradlew build`
  - Compiles code, runs tests, and assembles artifacts.
- **Run Application:** `gradlew run`
  - Starts the application locally.
- **Clean:** `gradlew clean`
  - Removes build artifacts.

### Testing
This project uses **JUnit 5** and **AssertJ**.

- **Run All Tests:** `gradlew test`
- **Run Unit Tests Only:** `gradlew unitTest`
- **Run Integration Tests Only:** `gradlew integrationTest`
- **Run a Single Test Class:**
  ```bash
  gradlew test --tests "io.github.seerainer.textedit.UndoManagerTest"
  ```
- **Run a Single Test Method:**
  ```bash
  gradlew test --tests "io.github.seerainer.textedit.UndoManagerTest.testPush"
  ```

### Native Image
- **Build Native Image:** `gradlew nativeCompile`
  - Generates a standalone executable using GraalVM.

## 2. Code Style & Conventions

Adhere strictly to the existing coding style.

### General Java Guidelines
- **Java Version:** The project targets Java 25.
- **Type Inference:** Use `var` for local variables whenever the type is clear from the context.
  - *Yes:* `final var display = Display.getDefault();`
  - *No:* `final Display display = Display.getDefault();`
- **Immutability:** Mark local variables and method parameters as `final` whenever possible.
  - *Example:* `public void push(final String snapshot) { ... }`
- **Formatting:**
  - Indentation: 4 spaces.
  - Braces: Egyptian style (at the end of the line).
  - Imports: Standard Java grouping. Avoid wildcard imports (e.g., `import java.util.*;`).

### SWT (Standard Widget Toolkit) Guidelines
- **UI Thread:** All UI updates must occur on the main UI thread.
  - Use `display.asyncExec(() -> { ... })` or `display.syncExec(() -> { ... })` if calling from a background thread.
- **Resource Management:** SWT resources (Colors, Fonts, Images) must be explicitly disposed if they were created by the application (not system defaults).
- **Widgets:** Do not assume standard Swing/AWT components; use `org.eclipse.swt.widgets.*`.

### Error Handling
- Use standard Java try/catch blocks.
- Propagate exceptions when appropriate, or handle them gracefully with logging (avoid `e.printStackTrace()` in production code if a logger is available).

### Naming Conventions
- **Classes:** PascalCase (e.g., `UndoManager`)
- **Methods:** camelCase (e.g., `canUndo`)
- **Variables:** camelCase (e.g., `capacity`)
- **Constants:** UPPER_SNAKE_CASE (e.g., `MAX_CAPACITY`)

## 3. Project Structure
- `src/main/java`: Application source code.
- `src/test/java`: Test source code.
- `src/main/resources`: Non-code assets (images, configs).
- `build.gradle`: Project configuration and dependencies.

## 4. Agent Operational Rules
- **Context Awareness:** Always read the file you are about to modify *before* editing it to understand the existing style and imports.
- **Path Handling:** Always use **absolute paths** when using file system tools.
- **Verification:** After making changes, run the tests to ensure no regressions:
  ```bash
  gradlew test
  ```
- **No Reversions:** Do not revert changes unless explicitly instructed by the user or if a critical error occurs that cannot be fixed otherwise.
