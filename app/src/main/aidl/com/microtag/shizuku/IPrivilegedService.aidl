package com.microtag.shizuku;

import com.microtag.shizuku.IClipListener;

interface IPrivilegedService {
    // Destroy method required by Shizuku's UserService contract.
    void destroy() = 16777114;

    void startClipboard(IClipListener listener) = 1;
    void stopClipboard() = 2;

    // Battery/standby exemptions + listener auto-enable. Returns a log of what ran.
    String hardenBackground(String pkg, String listenerComponent) = 3;

    // Starts the PROCESS_TEXT inspector from the shell (allowed to start activities from background).
    boolean launchText(String component, String text) = 4;
}
