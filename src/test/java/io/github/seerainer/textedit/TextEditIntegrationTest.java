package io.github.seerainer.textedit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("integration")
class TextEditIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void testEmptyFileHandling() throws IOException {
	final var emptyFile = tempDir.resolve("empty.txt");
	Files.writeString(emptyFile, "", StandardCharsets.UTF_8);

	final var loaded = FileIO.readWithBomDetection(emptyFile);

	assertThat(loaded.text()).isEmpty();
	assertThat(loaded.encoding()).isEqualTo("UTF-8");
    }

    @Test
    void testFileIOIntegration() throws IOException {
	// Test file creation and reading
	final var testFile = tempDir.resolve("integration_test.txt");
	final var content = "Integration test content\nwith multiple lines\r\nand different EOL styles";

	FileIO.writeUtf8(testFile.toFile(), content);

	final var loaded = FileIO.readWithBomDetection(testFile);

	assertThat(loaded.text()).isEqualTo(content);
	assertThat(loaded.encoding()).isEqualTo("UTF-8");
    }

    @SuppressWarnings("static-method")
    @Test
    void testLargeUndoStack() {
	final var undoManager = new UndoManager(100);

	for (var i = 0; i < 150; i++) {
	    undoManager.push("state" + i);
	}

	// Should only keep last 100
	assertThat(undoManager.undo()).isEqualTo("state149");

	// Undo 99 more times
	for (var i = 0; i < 99; i++) {
	    undoManager.undo();
	}

	// Should be at state50 now
	assertThat(undoManager.undo()).isNull();
    }

    @Test
    void testMultipleFileEncodings() throws IOException {
	// Test UTF-8
	final var utf8File = tempDir.resolve("utf8.txt");
	final var utf8Content = "Hello, 世界! 🌍";
	FileIO.writeUtf8(utf8File.toFile(), utf8Content);
	var loaded = FileIO.readWithBomDetection(utf8File);
	assertThat(loaded.text()).isEqualTo(utf8Content);
	assertThat(loaded.encoding()).isEqualTo("UTF-8");

	// Test UTF-8 with BOM
	final var bomFile = tempDir.resolve("utf8bom.txt");
	final var bom = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
	final var textBytes = "BOM test".getBytes(StandardCharsets.UTF_8);
	final var withBom = new byte[bom.length + textBytes.length];
	System.arraycopy(bom, 0, withBom, 0, bom.length);
	System.arraycopy(textBytes, 0, withBom, bom.length, textBytes.length);
	Files.write(bomFile, withBom);

	loaded = FileIO.readWithBomDetection(bomFile);
	assertThat(loaded.text()).isEqualTo("BOM test");
	assertThat(loaded.encoding()).isEqualTo("UTF-8 (BOM)");
    }

    @SuppressWarnings("static-method")
    @Test
    void testUndoManagerIntegration() {
	final var undoManager = new UndoManager(10);

	// Simulate text editing operations
	undoManager.push("");
	undoManager.push("H");
	undoManager.push("He");
	undoManager.push("Hel");
	undoManager.push("Hell");
	undoManager.push("Hello");

	assertThat(undoManager.canUndo()).isTrue();
	assertThat(undoManager.undo()).isEqualTo("Hello");
	assertThat(undoManager.undo()).isEqualTo("Hell");
	assertThat(undoManager.undo()).isEqualTo("Hel");
    }
}