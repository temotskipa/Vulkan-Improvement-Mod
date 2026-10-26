package com.temotskipa.vulkanimprovement.client.vulkan.gpu;

import org.jspecify.annotations.Nullable;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;

import java.util.*;
import java.util.function.LongConsumer;

/**
 * Framebuffers kept across frames. Minecraft's Vulkan command encoder creates a framebuffer for
 * every render pass and destroys it two submits later, although a frame opens the same render
 * passes over the same views as the frame before. Here a render pass over the same views and size
 * gets the framebuffer it had. A framebuffer is destroyed, through Minecraft's destruction queue,
 * once one of its views closes: Minecraft destroys a view only after it closes, so its handle
 * cannot be reused by another view while a framebuffer here still names it.
 * <p>
 * Used on the render thread only, as Minecraft's encoder is.
 */
public final class FramebufferCache {
    private static volatile @Nullable FramebufferCache latest;
    
    private final LongConsumer destroyLater;
    private final Map<FramebufferKey, Long> framebuffers = new HashMap<>();
    private final Map<Long, List<FramebufferKey>> keysByView = new HashMap<>();
    private long created;
    private long reused;
    private long released;
    
    /**
     * @param destroyLater queues a framebuffer's destruction behind the commands that may still use
     *                     it
     */
    public FramebufferCache(LongConsumer destroyLater) {
        this.destroyLater = destroyLater;
        latest = this;
    }
    
    /**
     * The cache of the encoder created last, for diagnostics.
     */
    public static Optional<FramebufferCache> latest() {
        return Optional.ofNullable(latest);
    }
    
    /**
     * The framebuffer kept for what this create info describes, or {@code VK_NULL_HANDLE}.
     */
    public long find(VkFramebufferCreateInfo createInfo) {
        Long framebuffer = framebuffers.get(FramebufferKey.of(createInfo));
        if (framebuffer == null) {
            return VK10.VK_NULL_HANDLE;
        }
        reused++;
        return framebuffer;
    }
    
    /**
     * Keeps a framebuffer just created from this create info.
     */
    public void keep(VkFramebufferCreateInfo createInfo, long framebuffer) {
        FramebufferKey key = FramebufferKey.of(createInfo);
        Long replaced = framebuffers.put(key, framebuffer);
        if (replaced != null) {
            destroyLater.accept(replaced);
        }
        for (long view : key.views()) {
            if (view != VK10.VK_NULL_HANDLE) {
                keysByView.computeIfAbsent(view, _ -> new ArrayList<>()).add(key);
            }
        }
        created++;
    }
    
    /**
     * Releases every framebuffer with this view as an attachment. Called as the view closes.
     */
    public void release(long view) {
        List<FramebufferKey> keys = keysByView.remove(view);
        if (keys == null) {
            return;
        }
        for (FramebufferKey key : keys) {
            Long framebuffer = framebuffers.remove(key);
            if (framebuffer == null) {
                continue;
            }
            destroyLater.accept(framebuffer);
            released++;
            for (long other : key.views()) {
                List<FramebufferKey> otherKeys = keysByView.get(other);
                if (otherKeys != null) {
                    otherKeys.remove(key);
                    if (otherKeys.isEmpty()) {
                        keysByView.remove(other);
                    }
                }
            }
        }
    }
    
    /**
     * Releases every framebuffer, when the cache is switched off or the encoder is destroyed.
     */
    public void releaseAll() {
        framebuffers.values().forEach(destroyLater::accept);
        released += framebuffers.size();
        framebuffers.clear();
        keysByView.clear();
    }
    
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kept", framebuffers.size());
        map.put("created", created);
        map.put("reused", reused);
        map.put("released", released);
        return map;
    }
}