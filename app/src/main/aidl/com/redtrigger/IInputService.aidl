package com.redtrigger;

interface IInputService {
    /** Grant a runtime permission to the app (runs as shell uid). */
    void grantPermission(String packageName, String permission);

    /** Stop stale native TGK writers before this app takes ownership. */
    String prepareNativeOwner(String ownerPackageName);

    /** Enable RedMagic native TGK mapping. */
    void enableNativeTgk(int leftX, int leftY, int rightX, int rightY, int mode, int rapidFireCount);

    /** Disable RedMagic native TGK mapping. */
    void disableNativeTgk();

    /** Return current RedMagic native TGK status. */
    String getNativeTgkStatus();

    /** Return the foreground package as seen by RedMagic/Game Space settings. */
    String getForegroundPackage();

    /** Return recent and running packages visible to shell. */
    String getActivePackages();

    /** Sample native TGK input devices for shoulder-key events. */
    String probeShoulderKeys(int timeoutMs);

    /** Destroy the service. */
    void destroy();
}
