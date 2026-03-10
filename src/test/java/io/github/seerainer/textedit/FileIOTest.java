package io.github.seerainer.textedit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class FileIOTest {

    @TempDir
    Path tempDir;

    @SuppressWarnings("static-method")
    @Test
    void testLoadedTextRecord() {
	final var loaded = new FileIO.LoadedText("test", "UTF-8");

	assertThat(loaded.text()).isEqualTo("test");
	assertThat(loaded.encoding()).isEqualTo("UTF-8");
    }

    @Test
    void testReadEmptyFile() throws IOException {
	final var file = tempDir.resolve("empty.txt");
	Files.writeString(file, "", StandardCharsets.UTF_8);

	final var result = FileIO.readWithBomDetection(file);

	assertThat(result.text()).isEmpty();
	assertThat(result.encoding()).isEqualTo("UTF-8");
    }

    @Test
    void testReadUtf16BE() throws IOException {
	final var file = tempDir.resolve("utf16be.txt");
	final var content = "Hello UTF-16 BE";
	final var bom = new byte[] { (byte) 0xFE, (byte) 0xFF };
	final var textBytes = content.getBytes(StandardCharsets.UTF_16BE);
	final var withBom = new byte[bom.length + textBytes.length];
	System.arraycopy(bom, 0, withBom, 0, bom.length);
	System.arraycopy(textBytes, 0, withBom, bom.length, textBytes.length);
	Files.write(file, withBom);

	final var result = FileIO.readWithBomDetection(file);

	assertThat(result.text()).isEqualTo(content);
	assertThat(result.encoding()).isEqualTo("UTF-16 BE");
    }

    @Test
    void testReadUtf16LE() throws IOException {
	final var file = tempDir.resolve("utf16le.txt");
	final var content = "Hello UTF-16 LE";
	final var bom = new byte[] { (byte) 0xFF, (byte) 0xFE };
	final var textBytes = content.getBytes(StandardCharsets.UTF_16LE);
	final var withBom = new byte[bom.length + textBytes.length];
	System.arraycopy(bom, 0, withBom, 0, bom.length);
	System.arraycopy(textBytes, 0, withBom, bom.length, textBytes.length);
	Files.write(file, withBom);

	final var result = FileIO.readWithBomDetection(file);

	assertThat(result.text()).isEqualTo(content);
	assertThat(result.encoding()).isEqualTo("UTF-16 LE");
    }

    @Test
    void testReadUtf8File() throws IOException {
	final var file = tempDir.resolve("utf8.txt");
	final var content = "Hello, World! 你好世界";
	Files.writeString(file, content, StandardCharsets.UTF_8);

	final var result = FileIO.readWithBomDetection(file);

	assertThat(result.text()).isEqualTo(content);
	assertThat(result.encoding()).isEqualTo("UTF-8");
    }

    @Test
    void testReadUtf8WithBom() throws IOException {
	final var file = tempDir.resolve("utf8bom.txt");
	final var content = "Hello, World!";
	final var bom = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
	final var textBytes = content.getBytes(StandardCharsets.UTF_8);
	final var withBom = new byte[bom.length + textBytes.length];
	System.arraycopy(bom, 0, withBom, 0, bom.length);
	System.arraycopy(textBytes, 0, withBom, bom.length, textBytes.length);
	Files.write(file, withBom);

	final var result = FileIO.readWithBomDetection(file);

	assertThat(result.text()).isEqualTo(content);
	assertThat(result.encoding()).isEqualTo("UTF-8 (BOM)");
    }

    @Test
    void testWriteEmptyString() throws IOException {
	final var file = tempDir.resolve("empty_write.txt").toFile();

	FileIO.writeUtf8(file, "");

	final var written = Files.readString(file.toPath(), StandardCharsets.UTF_8);
	assertThat(written).isEmpty();
    }

    @Test
    void testWriteUtf8() throws IOException {
	final var file = tempDir.resolve("write.txt").toFile();
	final var content = "Test content with UTF-8 文字";

	FileIO.writeUtf8(file, content);

	final var written = Files.readString(file.toPath(), StandardCharsets.UTF_8);
	assertThat(written).isEqualTo(content);
    }
}
