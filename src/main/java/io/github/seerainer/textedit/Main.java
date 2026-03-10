package io.github.seerainer.textedit;

import org.eclipse.swt.widgets.Display;

public class Main {
    public static void main(final String[] args) {
	System.setProperty("org.eclipse.swt.display.useSystemTheme", "true");
	final var display = Display.getDefault();
	final var textedit = new TextEdit(display, args.length > 0 ? args[0] : null);
	final var shell = textedit.getShell();
	while (!shell.isDisposed()) {
	    if (!display.readAndDispatch()) {
		display.sleep();
	    }
	}
	display.dispose();
    }
}