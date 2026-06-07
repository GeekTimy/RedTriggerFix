package com.redtrigger;

interface IInputService {
    /** Grant a runtime permission to the app (runs as shell uid). */
    void grantPermission(String packageName, String permission);

    /** Stop stale native TGK writers before this app takes ownership. */
    String prepareNativeOwner(String ownerPackageName);

    /** Enable RedMagic native TGK mapping. */
    void enableNativeTgk(int leftX, int leftY, int rightX, int rightY, int mode, int rapidFireCount, boolean leftEnabled, boolean rightEnabled);

    /** Disable RedMagic native TGK mapping. */
    void disableNativeTgk();

    /** Fully release native TGK state (official clean-release primitive). */
    void releaseTgk();

    /** Return current RedMagic native TGK status. */
    String getNativeTgkStatus();

    /** Return the foreground package as seen by RedMagic/Game Space settings. */
    String getForegroundPackage();

    /** Return recent and running packages visible to shell. */
    String getActivePackages();

    /** Sample native TGK input devices for shoulder-key events (one-shot, fixed window). */
    String probeShoulderKeys(int timeoutMs);

    /** Continuous, event-driven shoulder-key probe (no fixed timeout). */
    void startShoulderProbe();
    void stopShoulderProbe();
    String getProbeCounts();

    /** Developer-options visual debug toggles, written via shell to Settings.System. */
    void setShowTouches(boolean enable);
    void setPointerLocation(boolean enable);
    String getDebugToggles();

    /** Destroy the service. */
    void destroy();
}
