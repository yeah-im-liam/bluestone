package yeah.im.liam.bluestone.compat;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Minecraft 26.3 renamed RedStoneWireBlock to RedstoneWireBlock; this applies the dust mixin written for the running
 * game.
 */
public final class WireMixinPlugin implements IMixinConfigPlugin {
    private static final String OLD_WIRE = "net/minecraft/world/level/block/RedStoneWireBlock.class";

    private boolean oldWire;

    @Override
    public void onLoad(String mixinPackage) {
        //#if MC >= 26.1
        oldWire = WireMixinPlugin.class.getClassLoader().getResource(OLD_WIRE) != null;
        //#else
        //$$ oldWire = true;
        //#endif
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".LegacyWireMixin")) {
            return oldWire;
        }
        if (mixinClassName.endsWith(".ModernWireMixin")) {
            return !oldWire;
        }
        return true;
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
}
