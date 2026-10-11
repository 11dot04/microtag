package com.microtag.shizuku;

// Implemented by the app, called from the privileged (shell) process.
oneway interface IClipListener {
    void onClip(String text);
}
