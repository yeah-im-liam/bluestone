package yeah.im.liam.bluestone.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * How the view of a rider turns with a cart on a wall or ceiling rail.
 */
public final class CartViewSettings {
    public enum Speed {
        SLOW(5.0F),
        NORMAL(10.0F),
        FAST(20.0F);

        public final float ease;

        Speed(float ease) {
            this.ease = ease;
        }

        public Component label() {
            return Component.translatable("bluestone.options.turn_speed." + name().toLowerCase(Locale.ROOT));
        }
    }

    private static @Nullable CartViewSettings settings;

    public boolean firstPerson = true;
    public boolean thirdPerson = false;
    public Speed speed = Speed.NORMAL;

    private CartViewSettings() {
    }

    public static CartViewSettings get() {
        if (settings == null) {
            settings = new CartViewSettings();
            settings.load();
        }
        return settings;
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("bluestone-client.properties");
    }

    private void load() {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file())) {
            properties.load(reader);
        } catch (IOException e) {
            return;
        }
        firstPerson = Boolean.parseBoolean(properties.getProperty("first_person_turns", "true"));
        thirdPerson = Boolean.parseBoolean(properties.getProperty("third_person_turns", "false"));
        try {
            speed = Speed.valueOf(properties.getProperty("turn_speed", "normal").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            speed = Speed.NORMAL;
        }
    }

    public void save() {
        Properties properties = new Properties();
        properties.setProperty("first_person_turns", Boolean.toString(firstPerson));
        properties.setProperty("third_person_turns", Boolean.toString(thirdPerson));
        properties.setProperty("turn_speed", speed.name().toLowerCase(Locale.ROOT));
        try {
            Files.createDirectories(file().getParent());
            try (Writer writer = Files.newBufferedWriter(file())) {
                properties.store(writer, "Bluestone: Over-Powered");
            }
        } catch (IOException e) {
            // Kept for this session only.
        }
    }
}
