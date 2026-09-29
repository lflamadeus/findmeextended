package com.lai.findmeextended.mixin;

import com.lai.findmeextended.FindMeMod;
import com.lai.findmeextended.client.ParticlePosition;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineAccessor {

    @Shadow
    protected abstract <T extends ParticleOptions> void register(ParticleType<T> particleType, ParticleProvider<T> particleProvider);

    @Inject(at = @At("HEAD"), method = "Lnet/minecraft/client/particle/ParticleEngine;registerProviders()V")
    private void registerProviders(CallbackInfo ci) {
        this.register(FindMeMod.FIND_ME_PARTICLE_TYPE, (particleOptions, clientLevel, d, e, f, g, h, i) -> new ParticlePosition(clientLevel, d, e, f, g, h, i));
    }
}
