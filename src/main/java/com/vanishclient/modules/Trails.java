package com.vanishclient.modules;

import com.vanishclient.VanishClientAddon;
import com.vanishclient.util.Fx;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;

import net.minecraft.util.math.Vec3d;

import java.util.*;

public class Trails extends Module {
    public enum ColorMode { Static, Rainbow, Gradient }
    public enum PlayerMode { Trail, Tail, None }
    public enum Style { Solid, Faded, Invert }

    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgStyle = settings.createGroup("Style");
    private final SettingGroup sgDims = settings.createGroup("Dimensions");

    // ---- Colors ----
    private final Setting<ColorMode> colorMode = sgColors.add(new EnumSetting.Builder<ColorMode>()
        .name("color-mode").description("How the trail is coloured.")
        .defaultValue(ColorMode.Static).build());

    private final Setting<SettingColor> color = sgColors.add(new ColorSetting.Builder()
        .name("color").description("Main trail colour.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<SettingColor> color2 = sgColors.add(new ColorSetting.Builder()
        .name("second-color").description("End colour of the gradient.")
        .defaultValue(new SettingColor(90, 170, 255, 255))
        .visible(() -> colorMode.get() == ColorMode.Gradient).build());

    private final Setting<Double> rainbowSpeed = sgColors.add(new DoubleSetting.Builder()
        .name("rainbow-speed").description("Rainbow cycles per second.")
        .defaultValue(0.3).min(0.05).sliderMax(2)
        .visible(() -> colorMode.get() == ColorMode.Rainbow).build());

    // ---- Render ----
    private final Setting<PlayerMode> players = sgRender.add(new EnumSetting.Builder<PlayerMode>()
        .name("players").description("Trail = full ribbon, Tail = short ribbon, None = hidden.")
        .defaultValue(PlayerMode.Trail).build());

    private final Setting<Boolean> friends = sgRender.add(new BoolSetting.Builder()
        .name("friends").description("Draw trails behind Meteor friends.")
        .defaultValue(true).build());

    private final Setting<Boolean> onlyF5 = sgRender.add(new BoolSetting.Builder()
        .name("only-f5").description("Draw your own trail only in third-person view (F5).")
        .defaultValue(true).build());

    // ---- Style ----
    private final Setting<Style> style = sgStyle.add(new EnumSetting.Builder<Style>()
        .name("style").description("Solid = even, Faded = fully fades out toward the tail, Invert = clear near you, strongest at the tail.")
        .defaultValue(Style.Faded).build());

    private final Setting<Boolean> renderHalf = sgStyle.add(new BoolSetting.Builder()
        .name("render-half").description("Render only the bottom half of the ribbon.")
        .defaultValue(false).build());

    private final Setting<Double> alphaFactor = sgStyle.add(new DoubleSetting.Builder()
        .name("alpha-factor").description("How fast the fade happens (higher = fades sooner). Ignored by Solid.")
        .defaultValue(50).min(0).max(100).sliderMax(100).build());

    private final Setting<Double> trailAlpha = sgStyle.add(new DoubleSetting.Builder()
        .name("trail-alpha").description("Overall opacity in percent.")
        .defaultValue(100).min(5).max(100).sliderMax(100).build());

    // ---- Dimensions ----
    private final Setting<Double> height = sgDims.add(new DoubleSetting.Builder()
        .name("height").description("Ribbon height in blocks.")
        .defaultValue(1.8).min(0.1).sliderMax(3).build());

    private final Setting<Integer> length = sgDims.add(new IntSetting.Builder()
        .name("length").description("How many ticks of movement the trail remembers.")
        .defaultValue(30).min(4).sliderMax(80).build());

    private final Setting<Double> yOffset = sgDims.add(new DoubleSetting.Builder()
        .name("y-offset").description("Moves the ribbon up or down.")
        .defaultValue(0).min(-2).max(2).sliderRange(-1, 1).build());

    private final Map<UUID, ArrayDeque<Vec3d>> traces = new HashMap<>();

    public Trails() {
        super(VanishClientAddon.CATEGORY, "trails", "Draws a ribbon trail behind players.");
    }

    @Override
    public void onDeactivate() {
        traces.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) { traces.clear(); return; }

        Set<UUID> seen = new HashSet<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            seen.add(p.getUuid());
            ArrayDeque<Vec3d> pts = traces.computeIfAbsent(p.getUuid(), k -> new ArrayDeque<>());
            pts.addFirst(Fx.pos(p));
            while (pts.size() > length.get()) pts.removeLast();
        }
        traces.keySet().retainAll(seen);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        boolean firstPerson = mc.options.getPerspective().isFirstPerson();
        double td = event.tickDelta;

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p.isSpectator()) continue;

            boolean self = p == mc.player;
            boolean friend = !self && Friends.get().isFriend(p);
            if (friend) { if (!friends.get()) continue; }
            else if (players.get() == PlayerMode.None) continue;
            if (self && onlyF5.get() && firstPerson) continue;

            ArrayDeque<Vec3d> trace = traces.get(p.getUuid());
            if (trace == null || trace.size() < 2) continue;

            // Newest sample is replaced by a position interpolated between the last two ticks.
            List<Vec3d> pts = new ArrayList<>(trace);
            pts.set(0, Fx.lerp(pts.get(1), pts.get(0), td));

            int n = pts.size();
            if (!friend && players.get() == PlayerMode.Tail) n = Math.max(2, (int) (n * 0.4));
            if (n < 2) continue;
            List<Vec3d> used = smooth(pts.subList(0, n), 4);

            double yBottom = yOffset.get();
            double yTop = yBottom + (renderHalf.get() ? height.get() / 2 : height.get());
            final int segs = used.size() - 1;

            Fx.ribbon(event.renderer, used, yBottom, yTop, i -> colorAt(i / (double) Math.max(1, segs - 1)));
        }
    }

    private Color colorAt(double t) {
        t = Math.max(0, Math.min(1, t));
        // exponent 0.5 .. 3.5: the higher the Alpha Factor, the sooner the trail disappears
        double exp = 0.5 + alphaFactor.get() / 100.0 * 3.0;
        double mul = switch (style.get()) {
            case Solid -> 1.0;
            case Faded -> Math.pow(1.0 - t, exp);
            case Invert -> Math.pow(t, exp);
        };
        int alpha = Fx.clampAlpha(trailAlpha.get() / 100.0 * 255.0 * mul);

        SettingColor c1 = color.get();
        return switch (colorMode.get()) {
            case Static -> new Color(c1.r, c1.g, c1.b, alpha);
            case Rainbow -> Fx.rainbow(rainbowSpeed.get(), t * 0.4, alpha);
            case Gradient -> Fx.mix(c1, color2.get(), t, alpha);
        };
    }

    /** Splits every segment into `k` pieces so colour and fade change smoothly along the ribbon. */
    private static List<Vec3d> smooth(List<Vec3d> in, int k) {
        List<Vec3d> out = new ArrayList<>(in.size() * k);
        for (int i = 0; i < in.size() - 1; i++) {
            for (int j = 0; j < k; j++) out.add(Fx.lerp(in.get(i), in.get(i + 1), j / (double) k));
        }
        out.add(in.get(in.size() - 1));
        return out;
    }
}
