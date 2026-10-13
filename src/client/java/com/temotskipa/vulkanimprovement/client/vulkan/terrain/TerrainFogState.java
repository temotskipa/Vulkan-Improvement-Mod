package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The fog Minecraft set up for the frame being drawn. Minecraft uploads it to a uniform buffer its
 * own terrain shaders read; the mesh terrain path is handed the same numbers as push constants.
 */
public final class TerrainFogState {
    /**
     * A start and end no terrain is far enough away to reach, which is how Minecraft itself
     * switches fog off.
     */
    private static final float NEVER = Float.MAX_VALUE;
    private static final Fog NONE = new Fog(false, 0.0F, 0.0F, 0.0F, 0.0F, NEVER, NEVER, NEVER, NEVER);
    private static volatile Fog current = NONE;
    
    private TerrainFogState() {
    }
    
    /**
     * @param enabled whether Minecraft's fog is switched on; with it off Minecraft binds a buffer
     *                of fog that never starts instead of the frame's
     */
    public static void record(boolean enabled, float red, float green, float blue, float alpha, float environmentalStart, float environmentalEnd, float renderDistanceStart, float renderDistanceEnd) {
        current = enabled ? new Fog(true, red, green, blue, alpha, environmentalStart, environmentalEnd, renderDistanceStart, renderDistanceEnd) : NONE;
    }
    
    public static void reset() {
        current = NONE;
    }
    
    /**
     * @param improvedFog whether the terrain being drawn fades into the sky and cloud images
     *                    behind it instead of the fog colour
     */
    static void write(ByteBuffer target, boolean improvedFog) {
        Fog fog = current;
        target.putFloat(fog.red()).putFloat(fog.green()).putFloat(fog.blue()).putFloat(fog.alpha());
        target.putFloat(fog.environmentalStart()).putFloat(fog.environmentalEnd()).putFloat(fog.renderDistanceStart()).putFloat(fog.renderDistanceEnd());
        target.putInt(improvedFog ? 1 : 0).putInt(0).putInt(0).putInt(0);
    }
    
    public static Map<String, Object> asMap() {
        Fog fog = current;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", fog.enabled());
        map.put("color", fog.red() + "," + fog.green() + "," + fog.blue() + "," + fog.alpha());
        map.put("environmentalStart", fog.environmentalStart());
        map.put("environmentalEnd", fog.environmentalEnd());
        map.put("renderDistanceStart", fog.renderDistanceStart());
        map.put("renderDistanceEnd", fog.renderDistanceEnd());
        return map;
    }
    
    private record Fog(boolean enabled, float red, float green, float blue, float alpha, float environmentalStart, float environmentalEnd, float renderDistanceStart, float renderDistanceEnd) {
    }
}