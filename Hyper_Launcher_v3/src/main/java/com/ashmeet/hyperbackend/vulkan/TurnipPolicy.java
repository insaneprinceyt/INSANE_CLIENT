package com.ashmeet.hyperbackend.vulkan;

/** Selects the Adreno-only driver and explicitly clears it for every other launch. */
public final class TurnipPolicy {
    private TurnipPolicy() { }

    public interface DriverSwitch {
        void setEnabled(boolean enabled);
    }

    public static boolean configure(String vendor, String renderer, int androidApi,
                                    boolean preferSystemDriver, DriverSwitch driver) {
        // Keep the same vendor/renderer qualification as GpuUtils.GLInfo.isAdreno().
        // Unknown graphics information must never enable a device-specific driver.
        boolean enabled = androidApi >= 28 && !preferSystemDriver
                && vendor != null && vendor.contains("Qualcomm")
                && renderer != null && renderer.contains("Adreno");
        driver.setEnabled(enabled);
        return enabled;
    }
}
