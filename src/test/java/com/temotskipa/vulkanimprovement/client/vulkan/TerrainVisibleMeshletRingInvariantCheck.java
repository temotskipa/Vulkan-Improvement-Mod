package com.temotskipa.vulkanimprovement.client.vulkan;

import java.util.Map;

public final class TerrainVisibleMeshletRingInvariantCheck {
    private TerrainVisibleMeshletRingInvariantCheck() {
    }

    public static void main(String[] args) {
        checkLinearAllocation();
        checkWrapBlockedUntilFenceAllowsReuse();
        checkWrapAllocation();
        checkCapacityFailures();
        checkDiagnostics();
        checkInvalidRingRejected();
    }

    private static void checkLinearAllocation() {
        TerrainVisibleMeshletRing.Allocation allocation = new TerrainVisibleMeshletRing(16, 4).allocate(6, false);

        require(allocation.ready(), "linear allocation must be ready");
        require(allocation.offset() == 4, "linear allocation must start at cursor");
        require(allocation.count() == 6, "linear allocation must keep requested count");
        require(allocation.nextOffset() == 10, "linear allocation must advance cursor");
        require(!allocation.wrapped(), "linear allocation must not report wrap");
        require(!allocation.wrapBlocked(), "linear allocation must not report blocked wrap");
    }

    private static void checkWrapBlockedUntilFenceAllowsReuse() {
        TerrainVisibleMeshletRing.Allocation allocation = new TerrainVisibleMeshletRing(16, 14).allocate(4, false);

        require(!allocation.ready(), "wrap allocation must block when reuse is not allowed");
        require(allocation.wrapBlocked(), "wrap allocation must report fence-blocked reuse");
        require(allocation.nextOffset() == 14, "blocked allocation must preserve cursor");
        require(!allocation.reason().isBlank(), "blocked allocation must include reason");
    }

    private static void checkWrapAllocation() {
        TerrainVisibleMeshletRing.Allocation allocation = new TerrainVisibleMeshletRing(16, 14).allocate(4, true);

        require(allocation.ready(), "wrap allocation must be ready when reuse is allowed");
        require(allocation.offset() == 0, "wrap allocation must restart at zero");
        require(allocation.nextOffset() == 4, "wrap allocation must advance from zero");
        require(allocation.wrapped(), "wrap allocation must report wrap");
        require(!allocation.wrapBlocked(), "allowed wrap must not report blocked reuse");
    }

    private static void checkCapacityFailures() {
        TerrainVisibleMeshletRing.Allocation empty = new TerrainVisibleMeshletRing(16, 4).allocate(0, true);
        require(!empty.ready(), "empty allocation must not be ready");

        TerrainVisibleMeshletRing.Allocation noCapacity = new TerrainVisibleMeshletRing(0, 0).allocate(1, true);
        require(!noCapacity.ready(), "allocation without capacity must not be ready");

        TerrainVisibleMeshletRing.Allocation tooLarge = new TerrainVisibleMeshletRing(16, 0).allocate(17, true);
        require(!tooLarge.ready(), "allocation larger than capacity must not be ready");
        require(!tooLarge.reason().isBlank(), "capacity failure must include reason");
    }

    private static void checkDiagnostics() {
        TerrainVisibleMeshletRing.Allocation allocation = new TerrainVisibleMeshletRing(16, 14).allocate(4, true);
        Map<String, Object> map = allocation.asMap();

        require(map.get("requestedRecords").equals(4), "diagnostics must include requested records");
        require(map.get("offset").equals(0), "diagnostics must include offset");
        require(map.get("count").equals(4), "diagnostics must include count");
        require(map.get("capacity").equals(16), "diagnostics must include capacity");
        require(map.get("nextOffset").equals(4), "diagnostics must include next offset");
        require(Boolean.TRUE.equals(map.get("wrapped")), "diagnostics must include wrap state");
        require(Boolean.TRUE.equals(map.get("ready")), "diagnostics must include readiness");
        require(Boolean.FALSE.equals(map.get("wrapBlocked")), "diagnostics must include blocked-wrap state");
    }

    private static void checkInvalidRingRejected() {
        requireThrows(() -> new TerrainVisibleMeshletRing(-1, 0), "negative capacity must be rejected");
        requireThrows(() -> new TerrainVisibleMeshletRing(1, -1), "negative cursor must be rejected");
        requireThrows(() -> new TerrainVisibleMeshletRing(1, 2), "cursor beyond capacity must be rejected");
    }

    private static void requireThrows(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
