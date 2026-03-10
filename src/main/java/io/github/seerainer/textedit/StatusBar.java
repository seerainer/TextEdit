package io.github.seerainer.textedit;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

/**
 * Encapsulates status bar display logic (encoding, EOL style, counts, caret,
 * mode).
 */
public final class StatusBar {
    private final Label label;

    public StatusBar(final Composite parent) {
	final var sep = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL);
	sep.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
	label = new Label(parent, SWT.NONE);
	label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    public void setDarkTheme() {
	if (label.isDisposed()) {
	    return;
	}
	TextEdit.setColors(label);
    }

    public void update(final String content, final String encoding) {
	if (label.isDisposed()) {
	    return;
	}
	final var str = content == null ? "" : content;
	final var charCount = str.length();
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
	    if ((hasCRLF ? 1 : 0) + (hasLF ? 1 : 0) + (hasCR ? 1 : 0) > 1) {
		/* mixed */ }
	}
	final var types = (hasCRLF ? 1 : 0) + (hasLF ? 1 : 0) + (hasCR ? 1 : 0);
	final var eol = types > 1 ? "Mixed" : hasCRLF ? "CRLF" : hasLF ? "LF" : hasCR ? "CR" : "None";
	final var sb = new StringBuilder().append(encoding).append(" | ").append(eol).append(" | Chars: ")
		.append(charCount);
	label.setText(sb.toString());
    }
}