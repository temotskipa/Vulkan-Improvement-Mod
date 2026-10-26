package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

record ResidentPageTableChanges(boolean clearAll, int[] freedSlots) {
    boolean any() {
        return this.clearAll || this.freedSlots.length > 0;
    }
}