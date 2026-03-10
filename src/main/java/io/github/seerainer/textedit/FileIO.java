package io.github.seerainer.textedit;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Utility for reading and writing text files with BOM / encoding detection. */
public final class FileIO {
    private FileIO() {
    }

    private static boolean isValidUtf8(final byte[] data) {
	final var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
		.onUnmappableCharacter(CodingErrorAction.REPORT);
	try {
	    decoder.decode(ByteBuffer.wrap(data));
	    return true;
	} catch (final CharacterCodingException e) {
	    return false;
	}
    }

    public static LoadedText readWithBomDetection(final Path path) throws IOException {
	try (var raf = new RandomAccessFile(path.toFile(), "rws")) {
	    final var all = new byte[(int) Files.size(path)];
	    raf.readFully(all);

	    if (all.length == 0) {
		return new LoadedText("", "UTF-8");
	    }
	    if (all.length >= 3 && (all[0] & 0xFF) == 0xEF && (all[1] & 0xFF) == 0xBB && (all[2] & 0xFF) == 0xBF) {
		return new LoadedText(new String(all, 3, all.length - 3, StandardCharsets.UTF_8), "UTF-8 (BOM)");
	    }
	    if (all.length >= 4 && (all[0] & 0xFF) == 0xFF && (all[1] & 0xFF) == 0xFE && (all[2] & 0xFF) == 0x00
		    && (all[3] & 0xFF) == 0x00) {
		return new LoadedText(new String(all, 4, all.length - 4, StandardCharsets.UTF_32LE), "UTF-32 LE");
	    }
	    if (all.length >= 2 && (all[0] & 0xFF) == 0xFF && (all[1] & 0xFF) == 0xFE) {
		return new LoadedText(new String(all, 2, all.length - 2, StandardCharsets.UTF_16LE), "UTF-16 LE");
	    }
	    if (all.length >= 2 && (all[0] & 0xFF) == 0xFE && (all[1] & 0xFF) == 0xFF) {
		return new LoadedText(new String(all, 2, all.length - 2, StandardCharsets.UTF_16BE), "UTF-16 BE");
	    }
	    if (all.length >= 4 && (all[0] & 0xFF) == 0x00 && (all[1] & 0xFF) == 0x00 && (all[2] & 0xFF) == 0xFE
		    && (all[3] & 0xFF) == 0xFF) {
		return new LoadedText(new String(all, 4, all.length - 4, StandardCharsets.UTF_32BE), "UTF-32 BE");
	    }
	    if (isValidUtf8(all)) {
		return new LoadedText(new String(all, StandardCharsets.UTF_8), "UTF-8");
	    }
	    final var cs = Charset.defaultCharset();
	    return new LoadedText(new String(all, cs), cs.displayName());
	}
    }

    public static void writeUtf8(final File file, final String text) throws IOException {
	Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }

    public record LoadedText(String text, String encoding) {
    }
}
