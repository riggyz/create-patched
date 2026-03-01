package com.riggyz.riggyz_patches.plugin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.riggyz.riggyz_patches.Constants;

/**
 * Mixin plugin that gates mixins on both Create and Xaero's World Map
 * being present. Used for mixins targeting Create's Xaero compat classes,
 * which import Xaero types and can't be loaded without both mods.
 */
public class CreateXaeroMixinPlugin implements IMixinConfigPlugin {

    private static boolean areBothLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        boolean createLoaded = isClassPresent("com.simibubi.create.Create");
        boolean xaeroLoaded = isClassPresent("xaero.map.gui.GuiMap");
        areBothLoaded = createLoaded && xaeroLoaded;

        if (areBothLoaded) {
            Constants.LOG.info("Create + Xaero's detected, loading compat patches...");
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return areBothLoaded;
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
            Class.forName(className, false, CreateXaeroMixinPlugin.class.getClassLoader());
            
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
