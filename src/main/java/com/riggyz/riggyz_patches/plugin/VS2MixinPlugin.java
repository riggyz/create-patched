package com.riggyz.riggyz_patches.plugin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.riggyz.riggyz_patches.Constants;

/**
 * Mixin plugin that gates all mixins in the config based on whether
 * VS2 is present on the classpath. Used by riggyz_patches.vs2.mixins.json.
 */
public class VS2MixinPlugin implements IMixinConfigPlugin {

    public static boolean isVS2Loaded;

    @Override
    public void onLoad(String mixinPackage) {
        isVS2Loaded = isClassPresent("org.valkyrienskies.mod.common.ValkyrienSkiesMod");

        if (isVS2Loaded) {
            Constants.LOG.info("VS2 detected, loading patches...");
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return isVS2Loaded;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, VS2MixinPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
