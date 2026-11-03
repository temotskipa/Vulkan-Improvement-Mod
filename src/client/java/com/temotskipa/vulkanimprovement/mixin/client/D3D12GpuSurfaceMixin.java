package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.device.GpuSurface.Configuration;
import com.mojang.renderpearl.backend.vulkan.D3D12GpuSurface;
import com.mojang.renderpearl.util.dx.DXConst;
import com.mojang.renderpearl.util.dx.Win32Handle;
import com.mojang.renderpearl.util.dx.dxgi.DXGISwapchain3;
import com.temotskipa.vulkanimprovement.client.vulkan.presentation.PresentPacingController;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paces presents through DXGI, for the experimental Prefer DXGI Present option, as
 * {@link VulkanGpuSurfaceMixin} does on the Vulkan swapchain. With pacing on when Minecraft creates
 * its swapchain, the swapchain gets a frame latency object with the mod's latency and every present
 * waits on it; with pacing off it is created as Minecraft creates it.
 */
@Mixin(D3D12GpuSurface.class)
public final class D3D12GpuSurfaceMixin {
	/**
	 * Whether the swapchain being created is paced.
	 */
	@Unique
	private boolean vim$paced;
	/**
	 * Signalled by DXGI when a paced swapchain holds fewer frames than its maximum latency; null
	 * without one.
	 */
	@Unique
	private @Nullable Win32Handle vim$frameLatencyObject;

	@Inject(method = "configure", at = @At("HEAD"))
	private void vim$swapchainConfigured(Configuration config, CallbackInfo ci) {
		PresentPacingController.get().swapchainConfigured(config.presentMode());
	}

	@ModifyArg(method = "configure", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/util/dx/dxgi/DXGISwapchainDesc1;flags(I)V"))
	private int vim$createFrameLatencyObject(int flags) {
		this.vim$paced = PresentPacingController.get().pacesNewDxgiSwapchain();
		return this.vim$paced ? flags | DXConst.DXGI_SWAP_CHAIN_FLAG_FRAME_LATENCY_WAITABLE_OBJECT : flags;
	}

	/**
	 * Minecraft asks for a latency of two frames, which DXGI takes only from a swapchain with a
	 * frame latency object. A paced swapchain takes the mod's, which at its default is the same.
	 */
	@WrapOperation(method = "configure", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/util/dx/dxgi/DXGISwapchain3;setMaximumLatency(I)I"))
	private int vim$setMaximumFrameLatency(DXGISwapchain3 swapchain, int latency, Operation<Integer> original) {
		if (!this.vim$paced) {
			return original.call(swapchain, latency);
		}
		int result = original.call(swapchain, PresentPacingController.get().dxgiMaximumFrameLatency());
		Win32Handle frameLatencyObject = swapchain.waitHandle();
		this.vim$frameLatencyObject = frameLatencyObject.handle() == 0L ? null : frameLatencyObject;
		return result;
	}

	@Inject(method = "present", at = @At("HEAD"))
	private void vim$beforePresent(CallbackInfo ci) {
		PresentPacingController.get().beforePresent();
	}

	@Inject(method = "present", at = @At("RETURN"))
	private void vim$afterPresent(CallbackInfo ci) {
		PresentPacingController pacing = PresentPacingController.get();
		Win32Handle frameLatencyObject = this.vim$frameLatencyObject;
		if (frameLatencyObject != null) {
			pacing.afterDxgiPresent(frameLatencyObject);
		}
		pacing.afterPresent();
	}

	@Inject(method = "destroySwapchain", at = @At("HEAD"))
	private void vim$closeFrameLatencyObject(CallbackInfo ci) {
		Win32Handle frameLatencyObject = this.vim$frameLatencyObject;
		if (frameLatencyObject != null) {
			frameLatencyObject.close();
			this.vim$frameLatencyObject = null;
		}
	}
}
