#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif

#include <windows.h>
#include <commdlg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>
#include <shellapi.h>

#define APP_CLASS_NAME L"SimpleTextEditorWindowClass"
#define APP_TITLE_BASE L"TextEditor"

#define IDM_FILE_OPEN  1001
#define IDM_FILE_SAVE  1002
#define IDM_FILE_EXIT  1003

#define IDC_MAIN_EDIT  2001

// Edit commands
#define IDM_EDIT_UNDO       1101
#define IDM_EDIT_CUT        1102
#define IDM_EDIT_COPY       1103
#define IDM_EDIT_PASTE      1104
#define IDM_EDIT_DELETE     1105
#define IDM_EDIT_SELECTALL  1106

// View commands
#define IDM_VIEW_WORDWRAP   1201

static HWND  g_hEdit = NULL;
static WCHAR g_szFilePath[MAX_PATH] = L"";
static HMENU g_hEditMenu = NULL;
static HMENU g_hViewMenu = NULL;
static BOOL g_wordWrap = TRUE; // default: word wrap on

static void UpdateWindowTitle(HWND hWnd)
{
    WCHAR title[1024];
    if (g_szFilePath[0] == L'\0') {
        wcscpy_s(title, _countof(title), APP_TITLE_BASE);
    } else {
        // Extract filename from path
        const WCHAR* p = g_szFilePath;
        const WCHAR* lastSep = p;
        for (; *p; ++p) {
            if (*p == L'\\' || *p == L'/') lastSep = p + 1;
        }
        swprintf_s(title, _countof(title), L"%s - %s", APP_TITLE_BASE, lastSep);
    }
    SetWindowTextW(hWnd, title);
}

static BOOL LoadFileToEdit(HWND hEdit, const WCHAR* path)
{
    BOOL ok = FALSE;
    HANDLE hFile = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if (hFile == INVALID_HANDLE_VALUE) {
        MessageBoxW(NULL, L"Failed to open file.", APP_TITLE_BASE, MB_ICONERROR | MB_OK);
        return FALSE;
    }

    LARGE_INTEGER sizeLI;
    if (!GetFileSizeEx(hFile, &sizeLI)) {
        CloseHandle(hFile);
        MessageBoxW(NULL, L"Failed to get file size.", APP_TITLE_BASE, MB_ICONERROR | MB_OK);
        return FALSE;
    }

    if (sizeLI.QuadPart > (LONGLONG)(256LL * 1024LL * 1024LL)) {
        CloseHandle(hFile);
        MessageBoxW(NULL, L"File is too large to open (limit ~256 MB).", APP_TITLE_BASE, MB_ICONERROR | MB_OK);
        return FALSE;
    }

    DWORD size = (DWORD)sizeLI.QuadPart;
    if (size == 0) {
        // Empty file -> clear edit control and treat as successful load
        CloseHandle(hFile);
        SetWindowTextW(hEdit, L"");
        SendMessageW(hEdit, EM_SETMODIFY, FALSE, 0);
        SendMessageW(hEdit, EM_EMPTYUNDOBUFFER, 0, 0);
        return TRUE;
    }

    BYTE* buffer = (BYTE*)malloc(size + 4);
    if (!buffer) {
        CloseHandle(hFile);
        MessageBoxW(NULL, L"Out of memory.", APP_TITLE_BASE, MB_ICONERROR | MB_OK);
        return FALSE;
    }

    DWORD read = 0;
    if (!ReadFile(hFile, buffer, size, &read, NULL) || read != size) {
        free(buffer);
        CloseHandle(hFile);
        MessageBoxW(NULL, L"Failed to read file.", APP_TITLE_BASE, MB_ICONERROR | MB_OK);
        return FALSE;
    }
    CloseHandle(hFile);

    WCHAR* wtext = NULL;
    int wlen = 0;

    if (size >= 3 && buffer[0] == 0xEF && buffer[1] == 0xBB && buffer[2] == 0xBF) {
        // UTF-8 with BOM
        int needed = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, (LPCCH)(buffer + 3), size - 3, NULL, 0);
        if (needed > 0) {
            wtext = (WCHAR*)malloc((needed + 1) * sizeof(WCHAR));
            if (wtext) {
                wlen = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, (LPCCH)(buffer + 3), size - 3, wtext, needed);
                wtext[wlen] = L'\0';
            }
        }
    } else if (size >= 2 && buffer[0] == 0xFF && buffer[1] == 0xFE) {
        // UTF-16 LE with BOM
        DWORD u16bytes = size - 2;
        wlen = (int)(u16bytes / 2);
        wtext = (WCHAR*)malloc((wlen + 1) * sizeof(WCHAR));
        if (wtext) {
            memcpy(wtext, buffer + 2, u16bytes);
            wtext[wlen] = L'\0';
        }
    } else if (size >= 2 && buffer[0] == 0xFE && buffer[1] == 0xFF) {
        // UTF-16 BE with BOM -> convert to UTF-16 LE in-place
        DWORD u16bytes = size - 2;
        wlen = (int)(u16bytes / 2);
        wtext = (WCHAR*)malloc((wlen + 1) * sizeof(WCHAR));
        if (wtext) {
            const BYTE* src = buffer + 2;
            for (int i = 0; i < wlen; ++i) {
                wtext[i] = (WCHAR)((src[2 * i] << 8) | (src[2 * i + 1]));
            }
            wtext[wlen] = L'\0';
        }
    } else {
        // Try UTF-8 without BOM
        int needed = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, (LPCCH)buffer, size, NULL, 0);
        if (needed > 0) {
            wtext = (WCHAR*)malloc((needed + 1) * sizeof(WCHAR));
            if (wtext) {
                wlen = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, (LPCCH)buffer, size, wtext, needed);
                wtext[wlen] = L'\0';
            }
        } else {
            // Fallback to system ANSI code page
            needed = MultiByteToWideChar(CP_ACP, 0, (LPCCH)buffer, size, NULL, 0);
            if (needed > 0) {
                wtext = (WCHAR*)malloc((needed + 1) * sizeof(WCHAR));
                if (wtext) {
                    wlen = MultiByteToWideChar(CP_ACP, 0, (LPCCH)buffer, size, wtext, needed);
                    wtext[wlen] = L'\0';
                }
            }
        }
    }

    free(buffer);

    if (!wtext) {
        MessageBoxW(NULL, L"Failed to decode file text.", APP_TITLE_BASE, MB_ICONERROR | MB_OK);
        return FALSE;
    }

    SetWindowTextW(hEdit, wtext);
    SendMessageW(hEdit, EM_SETMODIFY, FALSE, 0);
    SendMessageW(hEdit, EM_EMPTYUNDOBUFFER, 0, 0);
    ok = TRUE;

    free(wtext);
    return ok;
}

static BOOL SaveEditToFile(HWND hEdit, const WCHAR* path)
{
    LRESULT len = SendMessageW(hEdit, WM_GETTEXTLENGTH, 0, 0);
    if (len < 0) len = 0;

    if (len == 0) {
        // Empty document -> create a zero-byte file (no BOM)
        HANDLE hFile = CreateFileW(path, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
        if (hFile == INVALID_HANDLE_VALUE) {
            MessageBoxW(NULL, L"Failed to create file.", APP_TITLE_BASE, MB_ICONERROR | MB_OK);
            return FALSE;
        }
        CloseHandle(hFile);
        SendMessageW(hEdit, EM_SETMODIFY, FALSE, 0);
        return TRUE;
    }

    WCHAR* wtext = (WCHAR*)malloc(((size_t)len + 1) * sizeof(WCHAR));
    if (!wtext) {
        MessageBoxW(NULL, L"Out of memory.", APP_TITLE_BASE, MB_ICONERROR | MB_OK);
        return FALSE;
    }
    GetWindowTextW(hEdit, wtext, (int)len + 1);

    int need = WideCharToMultiByte(CP_UTF8, 0, wtext, (int)len, NULL, 0, NULL, NULL);
    if (need < 0) need = 0;

    BYTE bom[3] = { 0xEF, 0xBB, 0xBF };
    BYTE* utf8 = (BYTE*)malloc(3 + (size_t)need);
    if (!utf8) {
        free(wtext);
        MessageBoxW(NULL, L"Out of memory.", APP_TITLE_BASE, MB_ICONERROR | MB_OK);
        return FALSE;
    }
    memcpy(utf8, bom, 3);
    if (need > 0) {
        WideCharToMultiByte(CP_UTF8, 0, wtext, (int)len, (LPSTR)(utf8 + 3), need, NULL, NULL);
    }

    HANDLE hFile = CreateFileW(path, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (hFile == INVALID_HANDLE_VALUE) {
        free(utf8);
        free(wtext);
        MessageBoxW(NULL, L"Failed to create file.", APP_TITLE_BASE, MB_ICONERROR | MB_OK);
        return FALSE;
    }

    DWORD written = 0;
    BOOL ok = WriteFile(hFile, utf8, 3 + (DWORD)need, &written, NULL);
    CloseHandle(hFile);

    free(utf8);
    free(wtext);

    if (!ok) {
        MessageBoxW(NULL, L"Failed to write file.", APP_TITLE_BASE, MB_ICONERROR | MB_OK);
        return FALSE;
    }

    SendMessageW(hEdit, EM_SETMODIFY, FALSE, 0);
    return TRUE;
}

static BOOL DoFileOpen(HWND hWnd)
{
    OPENFILENAMEW ofn = { 0 };
    WCHAR szPath[MAX_PATH] = L"";
    WCHAR filter[] = L"Text Files (*.txt)\0*.txt\0All Files (*.*)\0*.*\0\0";

    ofn.lStructSize = sizeof(ofn);
    ofn.hwndOwner = hWnd;
    ofn.lpstrFilter = filter;
    ofn.lpstrFile = szPath;
    ofn.nMaxFile = MAX_PATH;
    ofn.Flags = OFN_PATHMUSTEXIST | OFN_FILEMUSTEXIST | OFN_HIDEREADONLY;
    ofn.lpstrDefExt = L"txt";
    ofn.lpstrTitle = L"Open";

    if (GetOpenFileNameW(&ofn)) {
        if (LoadFileToEdit(g_hEdit, szPath)) {
            wcscpy_s(g_szFilePath, _countof(g_szFilePath), szPath);
            UpdateWindowTitle(hWnd);
            return TRUE;
        }
    }
    return FALSE;
}

static BOOL DoFileSave(HWND hWnd, BOOL forceSaveAs)
{
    WCHAR path[MAX_PATH] = L"";
    if (!forceSaveAs && g_szFilePath[0] != L'\0') {
        wcscpy_s(path, _countof(path), g_szFilePath);
    } else {
        OPENFILENAMEW ofn = { 0 };
        WCHAR filter[] = L"Text Files (*.txt)\0*.txt\0All Files (*.*)\0*.*\0\0";

        ofn.lStructSize = sizeof(ofn);
        ofn.hwndOwner = hWnd;
        ofn.lpstrFilter = filter;
        ofn.lpstrFile = path;
        ofn.nMaxFile = MAX_PATH;
        ofn.Flags = OFN_OVERWRITEPROMPT | OFN_PATHMUSTEXIST;
        ofn.lpstrDefExt = L"txt";
        ofn.lpstrTitle = L"Save As";

        if (!GetSaveFileNameW(&ofn)) {
            return FALSE; // canceled
        }
    }

    if (SaveEditToFile(g_hEdit, path)) {
        wcscpy_s(g_szFilePath, _countof(g_szFilePath), path);
        UpdateWindowTitle(hWnd);
        return TRUE;
    }
    return FALSE;
}

static BOOL PromptToSaveIfModified(HWND hWnd)
{
    BOOL modified = (BOOL)SendMessageW(g_hEdit, EM_GETMODIFY, 0, 0);
    if (!modified) return TRUE;

    int res = MessageBoxW(hWnd, L"The text has been modified.\nDo you want to save changes?", APP_TITLE_BASE,
                          MB_ICONQUESTION | MB_YESNOCANCEL);
    if (res == IDCANCEL) return FALSE;
    if (res == IDYES) {
        return DoFileSave(hWnd, FALSE);
    }
    return TRUE; // IDNO
}

// Helper: create or recreate the main edit control honoring g_wordWrap
static BOOL CreateMainEdit(HWND hWnd)
{
    // Preserve text and selection if control exists
    WCHAR *text = NULL;
    DWORD selStart = 0, selEnd = 0;
    BOOL modified = FALSE;

    if (g_hEdit) {
        LRESULT len = SendMessageW(g_hEdit, WM_GETTEXTLENGTH, 0, 0);
        if (len < 0) len = 0;
        text = (WCHAR*)malloc(((size_t)len + 1) * sizeof(WCHAR));
        if (text) {
            GetWindowTextW(g_hEdit, text, (int)len + 1);
        }
        SendMessageW(g_hEdit, EM_GETSEL, (WPARAM)&selStart, (LPARAM)&selEnd);
        modified = (BOOL)SendMessageW(g_hEdit, EM_GETMODIFY, 0, 0);
        DestroyWindow(g_hEdit);
        g_hEdit = NULL;
    }

    RECT rc;
    GetClientRect(hWnd, &rc);
    DWORD style = WS_CHILD | WS_VISIBLE | WS_VSCROLL | ES_LEFT | ES_MULTILINE | ES_AUTOVSCROLL;
    if (!g_wordWrap) {
        style |= WS_HSCROLL | ES_AUTOHSCROLL;
    }

    g_hEdit = CreateWindowExW(
        WS_EX_CLIENTEDGE,
        L"EDIT",
        L"",
        style,
        0, 0, rc.right - rc.left, rc.bottom - rc.top,
        hWnd,
        (HMENU)(UINT_PTR)IDC_MAIN_EDIT,
        (HINSTANCE)GetWindowLongPtrW(hWnd, GWLP_HINSTANCE),
        NULL);

    if (!g_hEdit) {
        MessageBoxW(hWnd, L"Failed to create edit control.", APP_TITLE_BASE, MB_ICONERROR | MB_OK);
        if (text) free(text);
        return FALSE;
    }

    HFONT hFont = (HFONT)GetStockObject(DEFAULT_GUI_FONT);
    SendMessageW(g_hEdit, WM_SETFONT, (WPARAM)hFont, TRUE);

    // Set a reasonable maximum text length (e.g., 16 MB)
    SendMessageW(g_hEdit, EM_LIMITTEXT, 16 * 1024 * 1024, 0);

    if (text) {
        SetWindowTextW(g_hEdit, text);
        SendMessageW(g_hEdit, EM_SETSEL, selStart, selEnd);
        if (!modified) SendMessageW(g_hEdit, EM_SETMODIFY, FALSE, 0);
        SendMessageW(g_hEdit, EM_EMPTYUNDOBUFFER, 0, 0);
        free(text);
    } else {
        SendMessageW(g_hEdit, EM_SETMODIFY, FALSE, 0);
        SendMessageW(g_hEdit, EM_EMPTYUNDOBUFFER, 0, 0);
    }

    return TRUE;
}

static void CreateAppMenu(HWND hWnd)
{
    HMENU hMenubar = CreateMenu();
    HMENU hFile = CreatePopupMenu();
    HMENU hEdit = CreatePopupMenu();
    HMENU hView = CreatePopupMenu();
    g_hEditMenu = hEdit;
    g_hViewMenu = hView;

    AppendMenuW(hFile, MF_STRING, IDM_FILE_OPEN, L"&Open\tCtrl+O");
    AppendMenuW(hFile, MF_STRING, IDM_FILE_SAVE, L"&Save\tCtrl+S");
    AppendMenuW(hFile, MF_SEPARATOR, 0, NULL);
    AppendMenuW(hFile, MF_STRING, IDM_FILE_EXIT, L"E&xit\tAlt+F4");

    AppendMenuW(hEdit, MF_STRING, IDM_EDIT_UNDO, L"&Undo\tCtrl+Z");
    AppendMenuW(hEdit, MF_SEPARATOR, 0, NULL);
    AppendMenuW(hEdit, MF_STRING, IDM_EDIT_CUT, L"Cu&t\tCtrl+X");
    AppendMenuW(hEdit, MF_STRING, IDM_EDIT_COPY, L"&Copy\tCtrl+C");
    AppendMenuW(hEdit, MF_STRING, IDM_EDIT_PASTE, L"&Paste\tCtrl+V");
    AppendMenuW(hEdit, MF_STRING, IDM_EDIT_DELETE, L"&Delete");
    AppendMenuW(hEdit, MF_SEPARATOR, 0, NULL);
    AppendMenuW(hEdit, MF_STRING, IDM_EDIT_SELECTALL, L"Select &All\tCtrl+A");

    // View menu: Word Wrap
    AppendMenuW(hView, MF_STRING | (g_wordWrap ? MF_CHECKED : MF_UNCHECKED), IDM_VIEW_WORDWRAP, L"Word Wrap");

    AppendMenuW(hMenubar, MF_POPUP, (UINT_PTR)hFile, L"&File");
    AppendMenuW(hMenubar, MF_POPUP, (UINT_PTR)hEdit, L"&Edit");
    AppendMenuW(hMenubar, MF_POPUP, (UINT_PTR)hView, L"&View");
    SetMenu(hWnd, hMenubar);
}

static LRESULT CALLBACK WndProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam)
{
    switch (msg) {
    case WM_CREATE:
    {
        CreateAppMenu(hWnd);

        // Create the main edit control (honors g_wordWrap)
        if (!CreateMainEdit(hWnd)) {
            PostQuitMessage(1);
            break;
        }

        UpdateWindowTitle(hWnd);

        if (g_szFilePath[0] != L'\0') {
            LoadFileToEdit(g_hEdit, g_szFilePath);
            UpdateWindowTitle(hWnd);
        }

        // Ensure the edit control has keyboard focus so the user can start typing immediately
        SetFocus(g_hEdit);

        // Accept file drag-and-drop onto the main window
        DragAcceptFiles(hWnd, TRUE);
    }
    break;

    case WM_DROPFILES:
    {
        HDROP hDrop = (HDROP)wParam;
        UINT count = DragQueryFileW(hDrop, 0xFFFFFFFF, NULL, 0);
        if (count > 0) {
            WCHAR path[MAX_PATH] = L"";
            if (DragQueryFileW(hDrop, 0, path, _countof(path))) {
                if (PromptToSaveIfModified(hWnd)) {
                    if (LoadFileToEdit(g_hEdit, path)) {
                        wcscpy_s(g_szFilePath, _countof(g_szFilePath), path);
                        UpdateWindowTitle(hWnd);
                    }
                }
            }
        }
        DragFinish(hDrop);
    }
    break;

    case WM_SETFOCUS:
        if (g_hEdit) {
            SetFocus(g_hEdit);
        }
        return 0;

    case WM_SIZE:
        if (g_hEdit) {
            MoveWindow(g_hEdit, 0, 0, LOWORD(lParam), HIWORD(lParam), TRUE);
        }
        break;

    case WM_COMMAND:
        switch (LOWORD(wParam)) {
        case IDM_FILE_OPEN:
            if (!PromptToSaveIfModified(hWnd)) break;
            DoFileOpen(hWnd);
            break;
        case IDM_FILE_SAVE:
            DoFileSave(hWnd, FALSE);
            break;
        case IDM_VIEW_WORDWRAP:
            // Toggle word wrap: recreate edit control preserving text
            g_wordWrap = !g_wordWrap;
            // Update menu check state immediately
            if (g_hViewMenu) {
                CheckMenuItem(g_hViewMenu, IDM_VIEW_WORDWRAP, MF_BYCOMMAND | (g_wordWrap ? MF_CHECKED : MF_UNCHECKED));
            }
            CreateMainEdit(hWnd);
            break;
        case IDM_EDIT_UNDO:
            if (g_hEdit) SendMessageW(g_hEdit, EM_UNDO, 0, 0);
            break;
        case IDM_EDIT_CUT:
            if (g_hEdit) SendMessageW(g_hEdit, WM_CUT, 0, 0);
            break;
        case IDM_EDIT_COPY:
            if (g_hEdit) SendMessageW(g_hEdit, WM_COPY, 0, 0);
            break;
        case IDM_EDIT_PASTE:
            if (g_hEdit) SendMessageW(g_hEdit, WM_PASTE, 0, 0);
            break;
        case IDM_EDIT_DELETE:
            if (g_hEdit) SendMessageW(g_hEdit, WM_CLEAR, 0, 0);
            break;
        case IDM_EDIT_SELECTALL:
            if (g_hEdit) {
                SendMessageW(g_hEdit, EM_SETSEL, 0, -1);
                SetFocus(g_hEdit);
            }
            break;
        case IDM_FILE_EXIT:
            PostMessageW(hWnd, WM_CLOSE, 0, 0);
            break;
        default:
            break;
        }
        break;

    case WM_INITMENUPOPUP:
    {
        HMENU hPopup = (HMENU)wParam;
        if (hPopup == g_hEditMenu && g_hEdit) {
            // Check selection
            DWORD selStart = 0, selEnd = 0;
            SendMessageW(g_hEdit, EM_GETSEL, (WPARAM)&selStart, (LPARAM)&selEnd);
            BOOL hasSel = (selStart != selEnd);

            // Can undo?
            BOOL canUndo = (BOOL)SendMessageW(g_hEdit, EM_CANUNDO, 0, 0);

            // Determine if clipboard has text
            // Determine if clipboard has text
            BOOL canPaste = IsClipboardFormatAvailable(CF_UNICODETEXT);
            // Determine if there is any text
            LRESULT textLen = SendMessageW(g_hEdit, WM_GETTEXTLENGTH, 0, 0);

            EnableMenuItem(hPopup, IDM_EDIT_UNDO, MF_BYCOMMAND | (canUndo ? MF_ENABLED : MF_GRAYED));
            EnableMenuItem(hPopup, IDM_EDIT_CUT, MF_BYCOMMAND | (hasSel ? MF_ENABLED : MF_GRAYED));
            EnableMenuItem(hPopup, IDM_EDIT_COPY, MF_BYCOMMAND | (hasSel ? MF_ENABLED : MF_GRAYED));
            EnableMenuItem(hPopup, IDM_EDIT_PASTE, MF_BYCOMMAND | (canPaste ? MF_ENABLED : MF_GRAYED));
            EnableMenuItem(hPopup, IDM_EDIT_DELETE, MF_BYCOMMAND | (hasSel ? MF_ENABLED : MF_GRAYED));
            EnableMenuItem(hPopup, IDM_EDIT_SELECTALL, MF_BYCOMMAND | (textLen > 0 ? MF_ENABLED : MF_GRAYED));
        }
        else if (hPopup == g_hViewMenu) {
            // Update check state for Word Wrap menu item
            CheckMenuItem(hPopup, IDM_VIEW_WORDWRAP, MF_BYCOMMAND | (g_wordWrap ? MF_CHECKED : MF_UNCHECKED));
        }
    }
    break;

    case WM_CLOSE:
        if (PromptToSaveIfModified(hWnd)) {
            DestroyWindow(hWnd);
        }
        return 0;

    case WM_DESTROY:
        PostQuitMessage(0);
        break;

    default:
        return DefWindowProcW(hWnd, msg, wParam, lParam);
    }
    return 0;
}

int APIENTRY wWinMain(HINSTANCE hInst, HINSTANCE hPrev, LPWSTR lpCmdLine, int nShowCmd)
{
    (void)hPrev;
    (void)lpCmdLine;

    int argc = 0;
    LPWSTR *argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    if (argv && argc >= 2) {
        wcscpy_s(g_szFilePath, _countof(g_szFilePath), argv[1]);
    }
    if (argv) LocalFree(argv);

    WNDCLASSW wc = { 0 };
    wc.lpfnWndProc = WndProc;
    wc.hInstance = hInst;
    wc.lpszClassName = APP_CLASS_NAME;
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    wc.hIcon = LoadIcon(NULL, IDI_APPLICATION);
    wc.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);

    if (!RegisterClassW(&wc)) {
        MessageBoxW(NULL, L"Window registration failed.", APP_TITLE_BASE, MB_ICONERROR | MB_OK);
        return 1;
    }

    HWND hWnd = CreateWindowExW(
        0,
        APP_CLASS_NAME,
        APP_TITLE_BASE,
        WS_OVERLAPPEDWINDOW,
        CW_USEDEFAULT, CW_USEDEFAULT, 800, 600,
        NULL, NULL, hInst, NULL);

    if (!hWnd) {
        MessageBoxW(NULL, L"Window creation failed.", APP_TITLE_BASE, MB_ICONERROR | MB_OK);
        return 1;
    }

    ShowWindow(hWnd, nShowCmd);
    UpdateWindow(hWnd);

    // Create keyboard accelerators: Ctrl+O = Open, Ctrl+S = Save
    ACCEL accels[] = {
        { FCONTROL | FVIRTKEY, (WORD)'O', IDM_FILE_OPEN },
        { FCONTROL | FVIRTKEY, (WORD)'S', IDM_FILE_SAVE },
        { FCONTROL | FVIRTKEY, (WORD)'Z', IDM_EDIT_UNDO },
        { FCONTROL | FVIRTKEY, (WORD)'X', IDM_EDIT_CUT },
        { FCONTROL | FVIRTKEY, (WORD)'C', IDM_EDIT_COPY },
        { FCONTROL | FVIRTKEY, (WORD)'V', IDM_EDIT_PASTE },
        { FCONTROL | FVIRTKEY, (WORD)'A', IDM_EDIT_SELECTALL }
    };
    HACCEL hAccel = CreateAcceleratorTableW(accels, _countof(accels));

    MSG msg;
    while (GetMessageW(&msg, NULL, 0, 0) > 0) {
        if (!(hAccel && TranslateAcceleratorW(hWnd, hAccel, &msg))) {
            TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }
    }

    if (hAccel) DestroyAcceleratorTable(hAccel);
    return (int)msg.wParam;
}