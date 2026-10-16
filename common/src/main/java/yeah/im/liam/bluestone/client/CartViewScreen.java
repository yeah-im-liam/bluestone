package yeah.im.liam.bluestone.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The mod's settings, opened from Mod Menu on Fabric and the mod list on NeoForge.
 */
public final class CartViewScreen extends Screen {
    private static final MethodHandle SET_SCREEN = setScreen();
    private final @Nullable Screen parent;

    public CartViewScreen(@Nullable Screen parent) {
        super(Component.translatable("bluestone.options.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        CartViewSettings settings = CartViewSettings.get();
        int x = width / 2 - 100;
        int y = height / 4;
        addRenderableWidget(new StringWidget((width - font.width(title)) / 2, y - 24, font.width(title), 9, title, font));
        addRenderableWidget(CycleButton.onOffBuilder(settings.firstPerson)
            .create(x, y, 200, 20, Component.translatable("bluestone.options.first_person"), (button, value) -> settings.firstPerson = value));
        addRenderableWidget(CycleButton.onOffBuilder(settings.thirdPerson)
            .create(x, y + 24, 200, 20, Component.translatable("bluestone.options.third_person"), (button, value) -> settings.thirdPerson = value));
        addRenderableWidget(CycleButton.builder(CartViewSettings.Speed::label, settings.speed)
            .withValues(List.of(CartViewSettings.Speed.values()))
            .create(x, y + 48, 200, 20, Component.translatable("bluestone.options.turn_speed"), (button, value) -> settings.speed = value));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(x, y + 84, 200, 20).build());
    }

    @Override
    public void onClose() {
        CartViewSettings.get().save();
        try {
            SET_SCREEN.invoke(minecraft, parent);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    // Minecraft 26.3 moved setScreen from Minecraft to its Gui.
    private static MethodHandle setScreen() {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        MethodType type = MethodType.methodType(void.class, Screen.class);
        try {
            return lookup.findVirtual(Minecraft.class, "setScreen", type);
        } catch (ReflectiveOperationException e) {
            try {
                return MethodHandles.filterArguments(lookup.findVirtual(Gui.class, "setScreen", type), 0, lookup.findGetter(Minecraft.class, "gui", Gui.class));
            } catch (ReflectiveOperationException e2) {
                throw new IllegalStateException(e2);
            }
        }
    }
}
