package com.redtrigger;

import com.redtrigger.IForegroundListener;

interface IInputService {
    /** Grant a runtime permission to the app (runs as shell uid). */
    void grantPermission(String packageName, String permission) = 0;

    /** Stop stale native TGK writers before this app takes ownership. */
    String prepareNativeOwner(String ownerPackageName) = 1;

    /** Enable RedMagic native TGK mapping. */
    void enableNativeTgk(int leftX, int leftY, int rightX, int rightY, int mode, int rapidFireCount, boolean leftEnabled, boolean rightEnabled) = 2;

    /** Disable RedMagic native TGK mapping. */
    void disableNativeTgk() = 3;

    /** Diagnostic only: this vendor method enables TGK; it does not cleanly release it. */
    void releaseTgk() = 4;

    /** Return current RedMagic native TGK status. */
    String getNativeTgkStatus() = 5;

    /** ActivityTaskManager foreground, with vendor settings only as a fallback. */
    String getForegroundPackage() = 6;

    /** Return recent and running packages visible to shell. */
    String getActivePackages() = 7;

    /** Sample native TGK input devices for shoulder-key events (one-shot, fixed window). */
    String probeShoulderKeys(int timeoutMs) = 8;

    /** Continuous, event-driven shoulder-key probe (no fixed timeout). */
    void startShoulderProbe() = 9;
    void stopShoulderProbe() = 10;
    String getProbeCounts() = 11;

    /** Developer-options visual debug toggles, written via shell to Settings.System. */
    void setShowTouches(boolean enable) = 12;
    void setPointerLocation(boolean enable) = 13;
    String getDebugToggles() = 14;

    /** Notify the app when Android's task stack changes; queries still determine the actual foreground. */
    void watchForeground(IForegroundListener listener) = 15;

    /** Shizuku's reserved destroy transaction. */
    void destroy() = 16777114;
}
