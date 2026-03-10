package io.github.seerainer.textedit;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.printing.PrintDialog;
import org.eclipse.swt.printing.Printer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

/**
 * Simple SWT text editor.
 */
public class TextEdit {

    private static final int UNDO_STACK_SIZE = 1000;
    private static final boolean DARK_THEME = Display.isSystemDarkTheme();
    private static final Color DARK_BACK = new Color(48, 48, 48);
    private static final Color DARK_FORE = new Color(255, 255, 255);
    private final UndoManager undoManager = new UndoManager(UNDO_STACK_SIZE);
    private final Display display;
    private final Shell shell;
    private Composite mainComposite;
    private File currentFile;
    private MenuItem miUndo;
    private MenuItem miCut;
    private MenuItem miCopy;
    private MenuItem miPaste;
    private MenuItem miDelete;
    private MenuItem miSelectAll;
    private MenuItem miWordWrap;
    private StatusBar statusBar;
    private MenuItem ctxUndo;
    private MenuItem ctxCut;
    private MenuItem ctxCopy;
    private MenuItem ctxPaste;
    private MenuItem ctxDelete;
    private MenuItem ctxSelectAll;
    private String beforeChangeText;
    private String currentEncoding = "UTF-8";
    private StyledText text;
    private boolean dirty = false;
    private boolean suppressModify;
    private boolean wordWrap = true;

    public TextEdit(final Display display, final String arg) {
	this.display = Objects.requireNonNull(display, "display must not be null");
	applyShellTheme();
	this.shell = new Shell(display, SWT.SHELL_TRIM);
	shell.setImage(display.getSystemImage(SWT.ICON_INFORMATION));
	shell.setText("TextEdit");
	shell.setSize(800, 600);
	shell.setLayout(getLayout());
	shell.addDisposeListener(_ -> {
	    DARK_BACK.dispose();
	    DARK_FORE.dispose();
	});

	createMainComposite();
	createMenuBar();
	createTextWidget();
	createStatusBar();
	createContextMenu();
	hookListeners();
	enableFileDrop();
	updateStatusBar();
	applyTheme();

	shell.open();

	if (arg != null && !arg.isBlank()) {
	    openFile(new File(arg));
	}
    }

    private static GridLayout getLayout() {
	final var layout = new GridLayout();
	layout.horizontalSpacing = 0;
	layout.marginHeight = 0;
	layout.marginWidth = 0;
	layout.verticalSpacing = 0;
	return layout;
    }

    private static MenuItem separator(final Menu fileMenu) {
	return new MenuItem(fileMenu, SWT.SEPARATOR);
    }

    static void setColors(final Control control) {
	if (control == null || control.isDisposed() || !DARK_THEME) {
	    return;
	}
	control.setBackground(DARK_BACK);
	control.setForeground(DARK_FORE);
    }

    private void applyShellTheme() {
	if ((!"win32".equals(SWT.getPlatform()) || !DARK_THEME)) {
	    return;
	}
	display.setData("org.eclipse.swt.internal.win32.useDarkModeExplorerTheme", Boolean.TRUE);
	display.setData("org.eclipse.swt.internal.win32.useShellTitleColoring", Boolean.TRUE);
	display.setData("org.eclipse.swt.internal.win32.all.use_WS_BORDER", Boolean.TRUE);
	display.setData("org.eclipse.swt.internal.win32.menuBarForegroundColor", DARK_FORE);
	display.setData("org.eclipse.swt.internal.win32.menuBarBackgroundColor", DARK_BACK);
    }

    private void applyTheme() {
	setColors(text);
	if (statusBar == null) {
	    return;
	}
	statusBar.setDarkTheme();
	updateStatusBar();
    }

    private void createContextMenu() {
	final var contextMenu = new Menu(text);
	text.setMenu(contextMenu);

	ctxUndo = new MenuItem(contextMenu, SWT.PUSH);
	ctxUndo.setText("&Undo");
	ctxUndo.addListener(SWT.Selection, _ -> doUndo());

	separator(contextMenu);

	ctxCut = new MenuItem(contextMenu, SWT.PUSH);
	ctxCut.setText("Cu&t");
	ctxCut.addListener(SWT.Selection, _ -> text.cut());

	ctxCopy = new MenuItem(contextMenu, SWT.PUSH);
	ctxCopy.setText("&Copy");
	ctxCopy.addListener(SWT.Selection, _ -> text.copy());

	ctxPaste = new MenuItem(contextMenu, SWT.PUSH);
	ctxPaste.setText("&Paste");
	ctxPaste.addListener(SWT.Selection, _ -> text.paste());

	ctxDelete = new MenuItem(contextMenu, SWT.PUSH);
	ctxDelete.setText("&Delete");
	ctxDelete.addListener(SWT.Selection, _ -> {
	    if (text.getSelectionCount() > 0) {
		text.insert("");
	    }
	});

	separator(contextMenu);

	ctxSelectAll = new MenuItem(contextMenu, SWT.PUSH);
	ctxSelectAll.setText("Select &All");
	ctxSelectAll.addListener(SWT.Selection, _ -> text.selectAll());

	contextMenu.addMenuListener(MenuListener.menuShownAdapter(_ -> updateContextMenuEnablement()));
    }

    private void createMainComposite() {
	if (mainComposite != null && !mainComposite.isDisposed()) {
	    return;
	}
	mainComposite = new Composite(shell, SWT.NONE);
	mainComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
	mainComposite.setLayout(getLayout());
    }

    private void createMenuBar() {
	final var bar = new Menu(shell, SWT.BAR);
	shell.setMenuBar(bar);

	// File Menu
	final var fileRoot = new MenuItem(bar, SWT.CASCADE);
	fileRoot.setText("&File");
	final var fileMenu = new Menu(shell, SWT.DROP_DOWN);
	fileRoot.setMenu(fileMenu);

	final var miNew = new MenuItem(fileMenu, SWT.PUSH);
	miNew.setText("&New\tCtrl+N");
	miNew.setAccelerator(SWT.CTRL + 'N');
	miNew.addListener(SWT.Selection, _ -> doNew());

	final var miOpen = new MenuItem(fileMenu, SWT.PUSH);
	miOpen.setText("&Open\tCtrl+O");
	miOpen.setAccelerator(SWT.CTRL + 'O');
	miOpen.addListener(SWT.Selection, _ -> doOpen());

	final var miSave = new MenuItem(fileMenu, SWT.PUSH);
	miSave.setText("&Save\tCtrl+S");
	miSave.setAccelerator(SWT.CTRL + 'S');
	miSave.addListener(SWT.Selection, _ -> doSave(false));

	final var miPrint = new MenuItem(fileMenu, SWT.PUSH);
	miPrint.setText("&Print\tCtrl+P");
	miPrint.setAccelerator(SWT.CTRL + 'P');
	miPrint.addListener(SWT.Selection, _ -> doPrint());

	separator(fileMenu);

	final var miExit = new MenuItem(fileMenu, SWT.PUSH);
	miExit.setText("E&xit\tAlt+F4");
	miExit.addListener(SWT.Selection, _ -> shell.close());

	// Edit Menu
	final var editRoot = new MenuItem(bar, SWT.CASCADE);
	editRoot.setText("&Edit");
	final var editMenu = new Menu(shell, SWT.DROP_DOWN);
	editRoot.setMenu(editMenu);

	miUndo = new MenuItem(editMenu, SWT.PUSH);
	miUndo.setText("&Undo\tCtrl+Z");
	miUndo.setAccelerator(SWT.CTRL + 'Z');
	miUndo.addListener(SWT.Selection, _ -> doUndo());

	separator(editMenu);

	miCut = new MenuItem(editMenu, SWT.PUSH);
	miCut.setText("Cu&t\tCtrl+X");
	miCut.setAccelerator(SWT.CTRL + 'X');
	miCut.addListener(SWT.Selection, _ -> text.cut());

	miCopy = new MenuItem(editMenu, SWT.PUSH);
	miCopy.setText("&Copy\tCtrl+C");
	miCopy.setAccelerator(SWT.CTRL + 'C');
	miCopy.addListener(SWT.Selection, _ -> text.copy());

	miPaste = new MenuItem(editMenu, SWT.PUSH);
	miPaste.setText("&Paste\tCtrl+V");
	miPaste.setAccelerator(SWT.CTRL + 'V');
	miPaste.addListener(SWT.Selection, _ -> text.paste());

	miDelete = new MenuItem(editMenu, SWT.PUSH);
	miDelete.setText("&Delete");
	miDelete.addListener(SWT.Selection, _ -> {
	    if (text.getSelectionCount() > 0) {
		text.insert("");
	    }
	});

	separator(editMenu);

	miSelectAll = new MenuItem(editMenu, SWT.PUSH);
	miSelectAll.setText("Select &All\tCtrl+A");
	miSelectAll.setAccelerator(SWT.CTRL + 'A');
	miSelectAll.addListener(SWT.Selection, _ -> text.selectAll());

	// View Menu
	final var viewRoot = new MenuItem(bar, SWT.CASCADE);
	viewRoot.setText("&View");
	final var viewMenu = new Menu(shell, SWT.DROP_DOWN);
	viewRoot.setMenu(viewMenu);

	miWordWrap = new MenuItem(viewMenu, SWT.CHECK);
	miWordWrap.setText("Word Wrap");
	miWordWrap.setSelection(true);
	miWordWrap.addListener(SWT.Selection, _ -> {
	    final var wrap = miWordWrap.getSelection();
	    recreateTextPreservingState(wrap);
	});

	editMenu.addMenuListener(MenuListener.menuShownAdapter(_ -> updateEditMenuEnablement()));
    }

    private void createStatusBar() {
	statusBar = new StatusBar(shell);
    }

    private void createTextWidget() {
	if (mainComposite == null || mainComposite.isDisposed()) {
	    createMainComposite();
	}
	var style = SWT.MULTI | SWT.V_SCROLL | SWT.BORDER;
	style |= wordWrap ? SWT.WRAP : SWT.H_SCROLL;
	text = new StyledText(mainComposite, style);
	text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
	setColors(text);
	text.addListener(SWT.KeyDown, _ -> {
	    if (!suppressModify) {
		beforeChangeText = text.getText();
	    }
	});
	text.addListener(SWT.MouseDown, _ -> {
	    if (!suppressModify) {
		beforeChangeText = text.getText();
	    }
	});
	text.addModifyListener(_ -> {
	    if (suppressModify) {
		return;
	    }
	    if (beforeChangeText != null && !beforeChangeText.equals(text.getText())) {
		undoManager.push(beforeChangeText);
	    }
	    setDirty(true);
	    updateStatusBar();
	});
	text.addListener(SWT.Selection, _ -> updateStatusBar());
    }

    private void doNew() {
	if (!promptToSaveIfDirty()) {
	    return;
	}
	suppressModify = true;
	text.setText("");
	text.setSelection(0);
	suppressModify = false;
	undoManager.clear();
	beforeChangeText = null;
	currentFile = null;
	setDirty(false);
	currentEncoding = "UTF-8";
	updateStatusBar();
    }

    private void doOpen() {
	if (!promptToSaveIfDirty()) {
	    return;
	}
	final var fd = new FileDialog(shell, SWT.OPEN);
	fd.setText("Open");
	fd.setFilterExtensions(new String[] { "*.txt", "*.*" });
	fd.setFilterNames(new String[] { "Text Files (*.txt)", "All Files (*.*)" });
	final var path = fd.open();
	if (path != null) {
	    openFile(new File(path));
	}
    }

    private void doPrint() {
	if (text == null || text.isDisposed() || text.getCharCount() == 0) {
	    return;
	}
	final var dlg = new PrintDialog(shell, SWT.NONE);
	final var data = dlg.open();
	if (data == null) {
	    return;
	}
	final var printer = new Printer(data);
	if (!printer.startJob(currentFile != null ? currentFile.getName() : "Document")) {
	    printer.dispose();
	    return;
	}
	final var gc = new GC(printer);
	try {
	    final var dpi = printer.getDPI();
	    final var marginX = dpi.x / 2; // 0.5" margins
	    final var marginY = dpi.y / 2;
	    final var clientArea = printer.getClientArea();
	    final var x = clientArea.x + marginX;
	    final var yStart = clientArea.y + marginY;
	    final var height = clientArea.height - marginY * 2;
	    var y = yStart;
	    final var lineHeight = gc.getFontMetrics().getHeight();
	    printer.startPage();
	    final var lines = text.getText().split("\r?\n", -1);
	    for (final var line : lines) {
		// Simple no-wrap printing: truncate visually if wider than page
		if (y + lineHeight > yStart + height) {
		    printer.endPage();
		    printer.startPage();
		    y = yStart;
		}
		gc.drawString(line, x, y, false);
		y += lineHeight;
	    }
	    printer.endPage();
	    printer.endJob();
	} finally {
	    gc.dispose();
	    printer.dispose();
	}
    }

    private boolean doSave(final boolean saveAs) {
	var target = currentFile;
	if (saveAs || target == null) {
	    final var fd = new FileDialog(shell, SWT.SAVE);
	    fd.setText("Save As");
	    fd.setFilterExtensions(new String[] { "*.txt", "*.*" });
	    fd.setFilterNames(new String[] { "Text Files (*.txt)", "All Files (*.*)" });
	    final var path = fd.open();
	    if (path == null) {
		return false;
	    }
	    target = new File(path);
	}
	if (!saveToFile(target)) {
	    return false;
	}
	currentFile = target;
	setDirty(false);
	return true;
    }

    private void doUndo() {
	final var previous = undoManager.undo();
	if (previous == null) {
	    return;
	}
	suppressModify = true;
	var caret = text.getSelection().x;
	text.setText(previous);
	if (caret > previous.length()) {
	    caret = previous.length();
	}
	text.setSelection(caret);
	suppressModify = false;
	setDirty(true);
	updateStatusBar();
    }

    private void enableFileDrop() {
	final var dt = new DropTarget(text, DND.DROP_COPY | DND.DROP_DEFAULT);
	dt.setTransfer(FileTransfer.getInstance());
	dt.addDropListener(new DropTargetAdapter() {
	    @Override
	    public void dragEnter(final DropTargetEvent event) {
		if (event.detail == DND.DROP_DEFAULT) {
		    event.detail = DND.DROP_COPY;
		}
	    }

	    @Override
	    public void dragOperationChanged(final DropTargetEvent event) {
		if (event.detail == DND.DROP_DEFAULT) {
		    event.detail = DND.DROP_COPY;
		}
	    }

	    @Override
	    public void dragOver(final DropTargetEvent event) {
		if (event.detail == DND.DROP_DEFAULT) {
		    event.detail = DND.DROP_COPY;
		}
	    }

	    @Override
	    public void drop(final DropTargetEvent event) {
		if (!FileTransfer.getInstance().isSupportedType(event.currentDataType)) {
		    return;
		}
		final var files = (String[]) event.data;
		if (files == null || files.length == 0 || !promptToSaveIfDirty()) {
		    return;
		}
		openFile(new File(files[0]));
	    }
	});
    }

    Shell getShell() {
	return shell;
    }

    private void hookListeners() {
	shell.addListener(SWT.Close, e -> {
	    if (!promptToSaveIfDirty()) {
		e.doit = false;
	    }
	});
    }

    private void openFile(final File file) {
	try {
	    final var loaded = FileIO.readWithBomDetection(file.toPath());
	    suppressModify = true;
	    text.setText(loaded.text());
	    suppressModify = false;
	    currentFile = file;
	    currentEncoding = loaded.encoding();
	    undoManager.clear();
	    setDirty(false);
	    updateStatusBar();
	} catch (final IOException ex) {
	    showError("Failed to open file: " + ex.getMessage());
	}
    }

    private boolean promptToSaveIfDirty() {
	if (!dirty) {
	    return true;
	}
	final var mb = new MessageBox(shell, SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
	mb.setText("Unsaved Changes");
	mb.setMessage("The text has been modified. Save changes?");
	final var res = mb.open();
	return res == SWT.YES ? doSave(false) : res == SWT.NO;
    }

    private void recreateTextPreservingState(final boolean newWrap) {
	if (text == null || text.isDisposed() || (this.wordWrap == newWrap)) {
	    return;
	}
	final var content = text.getText();
	final var selStart = text.getSelection().x;
	final var selEnd = text.getSelection().y;
	text.dispose();
	this.wordWrap = newWrap;
	createTextWidget();
	createContextMenu();
	suppressModify = true;
	text.setText(content);
	text.setSelection(Math.min(selStart, content.length()), Math.min(selEnd, content.length()));
	suppressModify = false;
	shell.layout(true, true);
	setColors(text);
	updateStatusBar();
    }

    private boolean saveToFile(final File file) {
	try {
	    FileIO.writeUtf8(file, text.getText());
	    currentEncoding = "UTF-8";
	    updateStatusBar();
	    return true;
	} catch (final IOException e) {
	    showError("Failed to save file: " + e.getMessage());
	    return false;
	}
    }

    private void setDirty(final boolean dirty) {
	this.dirty = dirty;
	updateShellTitle();
    }

    private void showError(final String msg) {
	final var mb = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
	mb.setText("Error");
	mb.setMessage(msg);
	mb.open();
    }

    private void updateContextMenuEnablement() {
	if (text == null || text.isDisposed()) {
	    return;
	}
	final var hasSel = text.getSelectionCount() > 0;
	ctxUndo.setEnabled(undoManager.canUndo());
	ctxCut.setEnabled(hasSel);
	ctxCopy.setEnabled(hasSel);
	ctxDelete.setEnabled(hasSel);
	var canPaste = false;
	Clipboard clipboard = null;
	try {
	    clipboard = new Clipboard(display);
	    final var clipData = clipboard.getContents(TextTransfer.getInstance());
	    canPaste = clipData instanceof String && !((String) clipData).isEmpty();
	} catch (final Exception ignored) {
	    canPaste = false;
	} finally {
	    if (clipboard != null) {
		clipboard.dispose();
	    }
	}
	ctxPaste.setEnabled(canPaste);
	ctxSelectAll.setEnabled(text.getCharCount() > 0);
    }

    private void updateEditMenuEnablement() {
	if (text == null || text.isDisposed()) {
	    return;
	}
	final var hasSel = text.getSelectionCount() > 0;
	miUndo.setEnabled(undoManager.canUndo());
	miCut.setEnabled(hasSel);
	miCopy.setEnabled(hasSel);
	miDelete.setEnabled(hasSel);
	var canPaste = false;
	Clipboard clipboard = null;
	try {
	    clipboard = new Clipboard(display);
	    final var clipData = clipboard.getContents(TextTransfer.getInstance());
	    canPaste = clipData instanceof String && !((String) clipData).isEmpty();
	} catch (final Exception ignored) {
	    canPaste = false;
	} finally {
	    if (clipboard != null) {
		clipboard.dispose();
	    }
	}
	miPaste.setEnabled(canPaste);
	miSelectAll.setEnabled(text.getCharCount() > 0);
    }

    private void updateShellTitle() {
	final var base = "TextEdit";
	final var name = currentFile == null ? "" : (" - " + currentFile.getName());
	final var mod = dirty ? " *" : "";
	shell.setText(new StringBuilder().append(base).append(name).append(mod).toString());
    }

    private void updateStatusBar() {
	if (statusBar == null || text == null || text.isDisposed()) {
	    return;
	}
	statusBar.update(text.getText(), currentEncoding);
    }
}
