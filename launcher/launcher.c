/* Lumen.exe — tiny GUI launcher that embeds the bundled Python runtime and runs lumen\main.py. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <shellapi.h>
#include <stdlib.h>
#include <wchar.h>

typedef int (*PyMainFn)(int, wchar_t **);

int WINAPI wWinMain(HINSTANCE inst, HINSTANCE prev, PWSTR cmd, int show)
{
    wchar_t dir[MAX_PATH * 2], dll[MAX_PATH * 2], script[MAX_PATH * 2];
    GetModuleFileNameW(NULL, dir, MAX_PATH * 2);
    wchar_t *slash = wcsrchr(dir, L'\\');
    if (slash) *slash = 0;
    swprintf(dll, MAX_PATH * 2, L"%ls\\app\\python313.dll", dir);
    swprintf(script, MAX_PATH * 2, L"%ls\\app\\lumen\\main.py", dir);
    wchar_t appdir[MAX_PATH * 2];
    swprintf(appdir, MAX_PATH * 2, L"%ls\\app", dir);

    SetDllDirectoryW(appdir);
    HMODULE py = LoadLibraryExW(dll, NULL, LOAD_WITH_ALTERED_SEARCH_PATH);
    if (!py) {
        MessageBoxW(NULL, L"Lumen could not load its Python runtime (python313.dll).\n"
                          L"Please keep Lumen.exe inside its folder.", L"Lumen", MB_ICONERROR);
        return 1;
    }
    PyMainFn py_main = (PyMainFn)GetProcAddress(py, "Py_Main");
    if (!py_main) return 2;

    int argc = 0;
    wchar_t **argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    wchar_t **args = (wchar_t **)calloc((size_t)argc + 6, sizeof(wchar_t *));
    int n = 0;
    args[n++] = argv[0];
    args[n++] = L"-X";
    args[n++] = L"utf8";
    args[n++] = L"-B";               /* don't write .pyc next to the app */
    args[n++] = script;
    for (int i = 1; i < argc; i++) args[n++] = argv[i];
    return py_main(n, args);
}
