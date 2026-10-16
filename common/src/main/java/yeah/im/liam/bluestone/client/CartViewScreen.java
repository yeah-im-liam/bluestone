package yeah.im.liam.bluestone.client;

//#if MC >= 26.1
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
//#endif
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The mod's settings, opened from Mod Menu on Fabric and the mod list on NeoForge.
 */
public final class CartViewScreen extends Screen {
    //#if MC >= 26.1
    private static final MethodHandle SET_SCREEN = setScreen();
    //#endif
    private final @Nullable Screen parent;

    public CartViewScreen(@Nullable Screen parent) {
        super(Component.translatable("bluestone.options.title"));
        this.parent = parent;
    }

    // Plain buttons rather than cycle buttons, whose internal names changed in 1.21.11.
    @Override
    protected void init() {
        CartViewSettings settings = CartViewSettings.get();
        int x = width / 2 - 100;
        int y = height / 4;
        addRenderableWidget(new StringWidget((width - font.width(title)) / 2, y - 24, font.width(title), 9, title, font));
        addRenderableWidget(Button.builder(onOff("bluestone.options.first_person", settings.firstPerson), button -> {
            settings.firstPerson = !settings.firstPerson;
            button.setMessage(onOff("bluestone.options.first_person", settings.firstPerson));
        }).bounds(x, y, 200, 20).build());
        addRenderableWidget(Button.builder(onOff("bluestone.options.third_person", settings.thirdPerson), button -> {
            settings.thirdPerson = !settings.thirdPerson;
            button.setMessage(onOff("bluestone.options.third_person", settings.thirdPerson));
        }).bounds(x, y + 24, 200, 20).build());
        addRenderableWidget(Button.builder(speed(settings.speed), button -> {
            CartViewSettings.Speed[] speeds = CartViewSettings.Speed.values();
            settings.speed = speeds[(settings.speed.ordinal() + 1) % speeds.length];
            button.setMessage(speed(settings.speed));
        }).bounds(x, y + 48, 200, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(x, y + 84, 200, 20).build());
    }

    private static Component onOff(String key, boolean value) {
        return CommonComponents.optionNameValue(Component.translatable(key), value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    private static Component speed(CartViewSettings.Speed speed) {
        return CommonComponents.optionNameValue(Component.translatable("bluestone.options.turn_speed"), speed.label());
    }

    @Override
    public void onClose() {
        CartViewSettings.get().save();
        //#if MC >= 26.1
        try {
            SET_SCREEN.invoke(minecraft, parent);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
        //#else
        //$$ minecraft.setScreen(parent);
        //#endif
    }

    //#if MC >= 26.1
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
    //#endif
}
