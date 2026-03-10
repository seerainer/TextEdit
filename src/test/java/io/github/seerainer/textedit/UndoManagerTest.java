package io.github.seerainer.textedit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class UndoManagerTest {

    private UndoManager undoManager;

    @BeforeEach
    void setUp() {
	undoManager = new UndoManager(5);
    }

    @Test
    void testCapacityLimit() {
	for (var i = 0; i < 10; i++) {
	    undoManager.push("text" + i);
	}

	// Should only keep the last 5 items
	assertThat(undoManager.undo()).isEqualTo("text9");
	assertThat(undoManager.undo()).isEqualTo("text8");
	assertThat(undoManager.undo()).isEqualTo("text7");
	assertThat(undoManager.undo()).isEqualTo("text6");
	assertThat(undoManager.undo()).isEqualTo("text5");
	assertThat(undoManager.canUndo()).isFalse();
    }

    @Test
    void testClear() {
	undoManager.push("first");
	undoManager.push("second");
	assertThat(undoManager.canUndo()).isTrue();

	undoManager.clear();
	assertThat(undoManager.canUndo()).isFalse();
    }

    @Test
    void testEmptyStringPush() {
	undoManager.push("");
	assertThat(undoManager.canUndo()).isTrue();
	assertThat(undoManager.undo()).isEqualTo("");
    }

    @Test
    void testInitiallyEmpty() {
	assertThat(undoManager.canUndo()).isFalse();
	assertThat(undoManager.undo()).isNull();
    }

    @Test
    void testMultiplePushAndUndo() {
	undoManager.push("first");
	undoManager.push("second");
	undoManager.push("third");

	assertThat(undoManager.canUndo()).isTrue();
	assertThat(undoManager.undo()).isEqualTo("third");
	assertThat(undoManager.undo()).isEqualTo("second");
	assertThat(undoManager.undo()).isEqualTo("first");
	assertThat(undoManager.canUndo()).isFalse();
    }

    @Test
    void testPushAndUndo() {
	undoManager.push("first");
	assertThat(undoManager.canUndo()).isTrue();
	assertThat(undoManager.undo()).isEqualTo("first");
	assertThat(undoManager.canUndo()).isFalse();
    }

    @Test
    void testPushNull() {
	undoManager.push(null);
	assertThat(undoManager.canUndo()).isFalse();
    }
}
