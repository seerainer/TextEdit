package io.github.seerainer.textedit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class StatusBarTest {

    @SuppressWarnings("static-method")
    @Test
    void testCRDetection() {
	final var str = "line1\rline2\rline3";
	var hasCRLF = false;
	var hasLF = false;
	var hasCR = false;

	for (var i = 0; i < str.length(); i++) {
	    final var c = str.charAt(i);
	    if (c == '\r') {
		if (i + 1 < str.length() && str.charAt(i + 1) == '\n') {
		    hasCRLF = true;
		    i++;
		} else {
		    hasCR = true;
		}
	    } else if (c == '\n') {
		hasLF = true;
	    }
	}

	assertThat(hasCR).isTrue();
	assertThat(hasCRLF).isFalse();
	assertThat(hasLF).isFalse();
    }

    @SuppressWarnings("static-method")
    @Test
    void testCRLFDetection() {
	final var str = "line1\r\nline2\r\nline3";
	var hasCRLF = false;
	var hasLF = false;
	var hasCR = false;

	for (var i = 0; i < str.length(); i++) {
	    final var c = str.charAt(i);
	    if (c == '\r') {
		if (i + 1 < str.length() && str.charAt(i + 1) == '\n') {
		    hasCRLF = true;
		    i++;
		} else {
		    hasCR = true;
		}
	    } else if (c == '\n') {
		hasLF = true;
	    }
	}

	assertThat(hasCRLF).isTrue();
	assertThat(hasLF).isFalse();
	assertThat(hasCR).isFalse();
    }

    @SuppressWarnings("static-method")
    @Test
    void testEOLDetectionLogic() {
	// Test LF
	final var str = "line1\nline2\nline3";
	var hasCRLF = false;
	var hasLF = false;
	var hasCR = false;

	for (var i = 0; i < str.length(); i++) {
	    final var c = str.charAt(i);
	    if (c == '\r') {
		if (i + 1 < str.length() && str.charAt(i + 1) == '\n') {
		    hasCRLF = true;
		    i++;
		} else {
		    hasCR = true;
		}
	    } else if (c == '\n') {
		hasLF = true;
	    }
	}

	assertThat(hasLF).isTrue();
	assertThat(hasCRLF).isFalse();
	assertThat(hasCR).isFalse();
    }

    @SuppressWarnings("static-method")
    @Test
    void testMixedEOLDetection() {
	final var str = "line1\nline2\r\nline3\rline4";
	var hasCRLF = false;
	var hasLF = false;
	var hasCR = false;

	for (var i = 0; i < str.length(); i++) {
	    final var c = str.charAt(i);
	    if (c == '\r') {
		if (i + 1 < str.length() && str.charAt(i + 1) == '\n') {
		    hasCRLF = true;
		    i++;
		} else {
		    hasCR = true;
		}
	    } else if (c == '\n') {
		hasLF = true;
	    }
	}

	assertThat(hasCRLF).isTrue();
	assertThat(hasLF).isTrue();
	assertThat(hasCR).isTrue();
    }
}