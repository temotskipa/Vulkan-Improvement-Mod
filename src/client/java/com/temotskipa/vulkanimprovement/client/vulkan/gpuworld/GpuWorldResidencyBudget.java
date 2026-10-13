package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record GpuWorldResidencyBudget(long canonicalMirrorBytes, long renderOnlyLodBytes, long predictiveDistantBytes, long visibilityCompactionBytes, long indirectCommandBytes, long maxResidentBytes) {
    private static final long MIB = 1024L * 1024L;
    
    public GpuWorldResidencyBudget {
        requireNonNegative(canonicalMirrorBytes, "canonicalMirrorBytes");
        requireNonNegative(renderOnlyLodBytes, "renderOnlyLodBytes");
        requireNonNegative(predictiveDistantBytes, "predictiveDistantBytes");
        requireNonNegative(visibilityCompactionBytes, "visibilityCompactionBytes");
        requireNonNegative(indirectCommandBytes, "indirectCommandBytes");
        if (maxResidentBytes <= 0L) {
            throw new IllegalArgumentException("maxResidentBytes must be positive: " + maxResidentBytes);
        }
        long reservedBytes = reservedBytes(canonicalMirrorBytes, renderOnlyLodBytes, predictiveDistantBytes, visibilityCompactionBytes, indirectCommandBytes);
        if (reservedBytes > maxResidentBytes) {
            throw new IllegalArgumentException("GPU world residency budget exceeds maxResidentBytes: " + reservedBytes + " > " + maxResidentBytes);
        }
    }
    
    public static GpuWorldResidencyBudget conservativeDefault() {
        return new GpuWorldResidencyBudget(384L * MIB, 64L * MIB, 32L * MIB, 16L * MIB, 4L * MIB, 512L * MIB);
    }
    
    private static void requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must be non-negative: " + value);
        }
    }
    
    private static long reservedBytes(long canonicalMirrorBytes, long renderOnlyLodBytes, long predictiveDistantBytes, long visibilityCompactionBytes, long indirectCommandBytes) {
        return checkedAdd(checkedAdd(checkedAdd(canonicalMirrorBytes, renderOnlyLodBytes), predictiveDistantBytes), checkedAdd(visibilityCompactionBytes, indirectCommandBytes));
    }
    
    private static long checkedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("GPU world residency budget overflows long", exception);
        }
    }
    
    public long pageKindBudgetBytes(GpuWorldPageKind pageKind) {
        return switch (Objects.requireNonNull(pageKind, "pageKind")) {
            case CANONICAL_MIRROR -> this.canonicalMirrorBytes;
            case RENDER_ONLY_LOD -> this.renderOnlyLodBytes;
            case PREDICTIVE_DISTANT -> this.predictiveDistantBytes;
        };
    }
    
    public long derivedWorkBudgetBytes() {
        return checkedAdd(this.visibilityCompactionBytes, this.indirectCommandBytes);
    }
    
    public long reservedBytes() {
        return reservedBytes(this.canonicalMirrorBytes, this.renderOnlyLodBytes, this.predictiveDistantBytes, this.visibilityCompactionBytes, this.indirectCommandBytes);
    }
    
    public boolean fitsWithinMaxResidentBytes() {
        return reservedBytes() <= this.maxResidentBytes;
    }
    
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("canonicalMirrorBytes", this.canonicalMirrorBytes);
        map.put("renderOnlyLodBytes", this.renderOnlyLodBytes);
        map.put("predictiveDistantBytes", this.predictiveDistantBytes);
        map.put("visibilityCompactionBytes", this.visibilityCompactionBytes);
        map.put("indirectCommandBytes", this.indirectCommandBytes);
        map.put("derivedWorkBudgetBytes", derivedWorkBudgetBytes());
        map.put("reservedBytes", reservedBytes());
        map.put("maxResidentBytes", this.maxResidentBytes);
        map.put("fitsWithinMaxResidentBytes", fitsWithinMaxResidentBytes());
        map.put("pageKindBudgetBytes", pageKindBudgetMap());
        return map;
    }
    
    private Map<String, Object> pageKindBudgetMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (GpuWorldPageKind pageKind : GpuWorldPageKind.values()) {
            map.put(pageKind.diagnosticName(), pageKindBudgetBytes(pageKind));
        }
        return map;
    }
}