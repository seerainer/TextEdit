package io.github.seerainer.textedit;

import java.util.ArrayDeque;
import java.util.Deque;

/** Simple bounded LIFO undo stack storing full text snapshots. */
public class UndoManager {
    private final int capacity;
    private final Deque<String> stack = new ArrayDeque<>();

    public UndoManager(final int capacity) {
	this.capacity = capacity;
    }

    public boolean canUndo() {
	return !stack.isEmpty();
    }

    public void clear() {
	stack.clear();
    }

    public void push(final String snapshot) {
	if (snapshot == null) {
	    return;
	}
	if (stack.size() >= capacity) {
	    stack.removeFirst();
	}
	stack.addLast(snapshot);
    }

    public String undo() {
	return stack.isEmpty() ? null : stack.removeLast();
    }
}
