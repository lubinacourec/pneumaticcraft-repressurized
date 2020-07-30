package me.desht.pneumaticcraft.common.heat;

import me.desht.pneumaticcraft.api.heat.IHeatExchangerLogic;
import me.desht.pneumaticcraft.common.network.DescSynced;

/**
 * Designed to sync temperature to client but only when changed by a certain amount, the delta being dependent on the
 * temperature at the time.  Include as a field in your TE and mark as @DescSynced, or in an Entity and use the data
 * manager to sync the temperature.
 */
public class SyncedTemperature {
    private static final int SYNC_RATE = 60;

    private final IHeatExchangerLogic logic;

    private int syncTimer = -1;
    private int pendingTemp;

    @DescSynced
    private int syncedTemp = -1;

    public SyncedTemperature(IHeatExchangerLogic logic) {
        this.logic = logic;
    }

    /**
     * Call client side to get the synced temperature.
     * @return the synced temperature
     */
    public int getSyncedTemp() {
        return syncedTemp;
    }

    /**
     * Call server side on a regular basis.
     */
    public void tick() {
        int currentTemp = logic.getTemperatureAsInt();

        if (shouldSyncNow()) {
            // large temperature delta: sync immediately
            this.syncedTemp = currentTemp;
            syncTimer = -1;
        } else if (currentTemp != syncedTemp) {
            // small temperature delta: schedule a sync to happen in 60 ticks, unless one is already scheduled
            if (syncTimer == -1) syncTimer = SYNC_RATE;
            pendingTemp = currentTemp;
        }

        if (syncTimer >= 0) {
            if (--syncTimer == -1) {
                this.syncedTemp = pendingTemp;
            }
        }
    }

    private boolean shouldSyncNow() {
        int currentTemp = logic.getTemperatureAsInt();

        if (syncedTemp < 0) return true; // initial sync

        int delta = Math.abs(syncedTemp - currentTemp);

        if (currentTemp < 73) {
            return false;
        } else if (currentTemp < 473) {
            return delta >= 10;
        } else if (currentTemp < 873) {
            return delta >= 30;
        } else if (currentTemp < 1473) {
            return delta >= 80;
        } else {
            return false;
        }
    }
}
