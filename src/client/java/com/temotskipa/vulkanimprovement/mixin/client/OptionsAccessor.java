package com.temotskipa.vulkanimprovement.mixin.client;

import java.io.File;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Options.class)
public interface OptionsAccessor {
    @Accessor("optionsFile")
    File vulkanimprovement$getOptionsFile();
}