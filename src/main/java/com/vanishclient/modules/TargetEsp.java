package com.vanishclient.modules;

import com.vanishclient.VanishClientAddon;
import com.vanishclient.util.Fx;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TargetEsp extends Module {
    public enum RenderType { Circle, Ghosts, Crystals, Lightning }
    public enum ColorMode { Static, Rainbow }
    public enum GhostTexture { Soft, Bloom, Smoke }
    public enum GhostPath { Standard, Spiral, Wave }

    private final SettingGroup sgRender = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgBehavior = settings.createGroup("Behavior");
    private final SettingGroup sgGhosts = settings.createGroup("Ghosts");
    private final SettingGroup sgCircle = settings.createGroup("Circle");
    private final SettingGroup sgCrystals = settings.createGroup("Crystals");
    private final SettingGroup sgLightning = settings.createGroup("Lightning");
    private final SettingGroup sgFriends = settings.createGroup("Friends Target Render");

    // ---- Render ----
    private final Setting<RenderType> renderType = sgRender.add(new EnumSetting.Builder<RenderType>()
        .name("render-type").description("Selects the type of target ESP.")
        .defaultValue(RenderType.Circle).build());

    // ---- Colors ----
    private final Setting<ColorMode> colorMode = sgColors.add(new EnumSetting.Builder<ColorMode>()
        .name("color-mode").description("How the effect is coloured.")
        .defaultValue(ColorMode.Static).build());

    private final Setting<SettingColor> color = sgColors.add(new ColorSetting.Builder()
        .name("color").description("Effect colour.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(() -> colorMode.get() == ColorMode.Static).build());

    // ---- Behavior ----
    private final Setting<Double> followTime = sgBehavior.add(new DoubleSetting.Builder()
        .name("follow-time").description("Seconds the ESP stays on a target after you stop hitting it.")
        .defaultValue(10).min(0.5).sliderMax(30).build());

    private final Setting<Double> animSpeed = sgBehavior.add(new DoubleSetting.Builder()
        .name("anim-speed").description("Animation speed multiplier.")
        .defaultValue(1).min(0.1).sliderMax(4).build());

    private final Setting<Double> maxDistance = sgBehavior.add(new DoubleSetting.Builder()
        .name("max-distance").description("Maximum distance to render the target.")
        .defaultValue(128).min(8).sliderMax(256).build());

    // ---- Ghosts ----
    private final Setting<GhostTexture> texture = sgGhosts.add(new EnumSetting.Builder<GhostTexture>()
        .name("texture").description("Soft = clean, Bloom = strong glow, Smoke = wide and faint.")
        .defaultValue(GhostTexture.Soft).build());

    private final Setting<GhostPath> path = sgGhosts.add(new EnumSetting.Builder<GhostPath>()
        .name("path").description("Ghost movement trajectory.")
        .defaultValue(GhostPath.Standard).build());

    private final Setting<Double> ghostLength = sgGhosts.add(new DoubleSetting.Builder()
        .name("length").description("Length of each ghost's tail.")
        .defaultValue(1.1).min(0.3).sliderMax(3).build());

    private final Setting<Integer> ghostCount = sgGhosts.add(new IntSetting.Builder()
        .name("count").description("Number of ghosts.")
        .defaultValue(3).min(1).sliderMax(8).build());

    private final Setting<Double> ghostSize = sgGhosts.add(new DoubleSetting.Builder()
        .name("size").description("Thickness of the ghosts.")
        .defaultValue(1).min(0.3).sliderMax(3).build());

    // ---- Circle ----
    private final Setting<Double> circleRadius = sgCircle.add(new DoubleSetting.Builder()
        .name("radius").description("Extra radius added around the target.")
        .defaultValue(0.15).min(0).sliderMax(1.5).visible(() -> uses(RenderType.Circle)).build());

    private final Setting<Boolean> circleBounce = sgCircle.add(new BoolSetting.Builder()
        .name("bounce").description("Ring moves up and down the body. Off = stays at the feet.")
        .defaultValue(true).visible(() -> uses(RenderType.Circle)).build());

    private final Setting<Double> circleBounceSpeed = sgCircle.add(new DoubleSetting.Builder()
        .name("bounce-speed").description("How fast the ring moves up and down.")
        .defaultValue(1.6).min(0.1).sliderMax(6).visible(() -> uses(RenderType.Circle) && circleBounce.get()).build());

    private final Setting<Integer> circleLayers = sgCircle.add(new IntSetting.Builder()
        .name("rings").description("How many rings are drawn (thickness / trail).")
        .defaultValue(4).min(1).sliderMax(10).visible(() -> uses(RenderType.Circle)).build());

    private final Setting<Double> circleSpacing = sgCircle.add(new DoubleSetting.Builder()
        .name("ring-spacing").description("Distance between the rings.")
        .defaultValue(0.04).min(0.005).sliderMax(0.3).visible(() -> uses(RenderType.Circle)).build());

    private final Setting<Boolean> circleGround = sgCircle.add(new BoolSetting.Builder()
        .name("ground-ring").description("Extra faint ring at the target's feet.")
        .defaultValue(true).visible(() -> uses(RenderType.Circle)).build());

    private final Setting<Integer> circleSegments = sgCircle.add(new IntSetting.Builder()
        .name("smoothness").description("How round the ring is.")
        .defaultValue(40).min(8).sliderMax(120).visible(() -> uses(RenderType.Circle)).build());

    // ---- Crystals ----
    private final Setting<Integer> crystalCount = sgCrystals.add(new IntSetting.Builder()
        .name("count").description("Number of crystals.")
        .defaultValue(28).min(4).sliderMax(120).visible(() -> uses(RenderType.Crystals)).build());

    private final Setting<Double> crystalSize = sgCrystals.add(new DoubleSetting.Builder()
        .name("size").description("Crystal size.")
        .defaultValue(0.045).min(0.01).sliderMax(0.2).visible(() -> uses(RenderType.Crystals)).build());

    private final Setting<Double> crystalRise = sgCrystals.add(new DoubleSetting.Builder()
        .name("rise-speed").description("How fast crystals float upward.")
        .defaultValue(0.5).min(0.05).sliderMax(3).visible(() -> uses(RenderType.Crystals)).build());

    private final Setting<Double> crystalRadius = sgCrystals.add(new DoubleSetting.Builder()
        .name("radius").description("How far from the body the crystals orbit (multiplier).")
        .defaultValue(1).min(0.3).sliderMax(3).visible(() -> uses(RenderType.Crystals)).build());

    private final Setting<Double> crystalHeight = sgCrystals.add(new DoubleSetting.Builder()
        .name("height").description("How high crystals rise (multiplier of body height).")
        .defaultValue(1.1).min(0.3).sliderMax(3).visible(() -> uses(RenderType.Crystals)).build());

    private final Setting<Double> crystalTwist = sgCrystals.add(new DoubleSetting.Builder()
        .name("twist").description("How much the crystals spiral while rising.")
        .defaultValue(4).min(0).sliderMax(12).visible(() -> uses(RenderType.Crystals)).build());

    private final Setting<Boolean> crystalSparkles = sgCrystals.add(new BoolSetting.Builder()
        .name("sparkle-lines").description("Draw a small cross on every crystal.")
        .defaultValue(true).visible(() -> uses(RenderType.Crystals)).build());

    // ---- Lightning ----
    private final Setting<Integer> boltCount = sgLightning.add(new IntSetting.Builder()
        .name("bolts").description("Number of bolts at once.")
        .defaultValue(5).min(1).sliderMax(20).visible(() -> uses(RenderType.Lightning)).build());

    private final Setting<Integer> boltSegments = sgLightning.add(new IntSetting.Builder()
        .name("segments").description("Zigzags per bolt.")
        .defaultValue(5).min(2).sliderMax(15).visible(() -> uses(RenderType.Lightning)).build());

    private final Setting<Double> boltJitter = sgLightning.add(new DoubleSetting.Builder()
        .name("jitter").description("How far each zigzag can jump.")
        .defaultValue(0.7).min(0.1).sliderMax(2).visible(() -> uses(RenderType.Lightning)).build());

    private final Setting<Integer> boltRefresh = sgLightning.add(new IntSetting.Builder()
        .name("flicker-ms").description("Milliseconds before new bolts appear (lower = faster flicker).")
        .defaultValue(90).min(20).sliderMax(500).visible(() -> uses(RenderType.Lightning)).build());

    private final Setting<Double> boltSpread = sgLightning.add(new DoubleSetting.Builder()
        .name("spread").description("How wide around the body the bolts start (multiplier).")
        .defaultValue(1.6).min(0.5).sliderMax(4).visible(() -> uses(RenderType.Lightning)).build());

    private final Setting<Boolean> boltGlow = sgLightning.add(new BoolSetting.Builder()
        .name("glow").description("Coloured glow line next to the white core.")
        .defaultValue(true).visible(() -> uses(RenderType.Lightning)).build());

    // ---- Friends ----
    private final Setting<Boolean> friendsEnabled = sgFriends.add(new BoolSetting.Builder()
        .name("enabled").description("Show the ESP on Meteor friends too.")
        .defaultValue(false).build());

    private final Setting<RenderType> friendType = sgFriends.add(new EnumSetting.Builder<RenderType>()
        .name("render-type").description("Effect used on friends.")
        .defaultValue(RenderType.Circle).build());

    private final Setting<SettingColor> friendColor = sgFriends.add(new ColorSetting.Builder()
        .name("color").description("Colour used on friends.")
        .defaultValue(new SettingColor(80, 255, 120, 255)).build());

    private LivingEntity target;
    private long lastSeen;
    private Vec3d prev, cur;

    public TargetEsp() {
        super(VanishClientAddon.CATEGORY, "target-esp", "Draws an effect on the entity you are fighting.");
    }

    @Override
    public void onDeactivate() {
        target = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) { target = null; return; }

        long now = System.currentTimeMillis();
        if (mc.options.attackKey.isPressed() && mc.targetedEntity instanceof LivingEntity le && le != mc.player) {
            if (target != le) { target = le; prev = cur = Fx.pos(le); }
            lastSeen = now;
        }

        if (target != null) {
            if (target.isRemoved() || target.getHealth() <= 0f || now - lastSeen > followTime.get() * 1000) {
                target = null;
            } else {
                prev = cur;
                cur = Fx.pos(target);
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (target == null || mc.player == null || prev == null || cur == null) return;
        if (mc.player.distanceTo(target) > maxDistance.get()) return;

        boolean friend = target instanceof PlayerEntity p && Friends.get().isFriend(p);
        if (friend && !friendsEnabled.get()) return;

        RenderType type = friend ? friendType.get() : renderType.get();
        Vec3d pos = Fx.lerp(prev, cur, event.tickDelta);
        double w = target.getWidth();
        double h = target.getHeight();
        double anim = System.currentTimeMillis() / 1000.0 * animSpeed.get();
        Renderer3D r = event.renderer;

        switch (type) {
            case Circle -> circle(r, pos, w, h, anim, friend);
            case Ghosts -> ghosts(r, pos, w, h, anim, friend);
            case Crystals -> crystals(r, pos, w, h, anim, friend);
            case Lightning -> lightning(r, pos, w, h, friend);
        }
    }

    /** True when the normal or the friend render type is `t`, so only relevant settings are shown. */
    private boolean uses(RenderType t) {
        return renderType.get() == t || friendType.get() == t;
    }

    private Color col(boolean friend, double offset, int alpha) {
        if (friend) {
            SettingColor c = friendColor.get();
            return new Color(c.r, c.g, c.b, alpha);
        }
        if (colorMode.get() == ColorMode.Rainbow) return Fx.rainbow(0.3, offset, alpha);
        SettingColor c = color.get();
        return new Color(c.r, c.g, c.b, alpha);
    }

    // ---------------------------------------------------------------- Circle

    private void circle(Renderer3D r, Vec3d pos, double w, double h, double anim, boolean friend) {
        double radius = w * 0.9 + circleRadius.get();
        int seg = circleSegments.get();
        boolean bounce = circleBounce.get();
        double y = bounce ? (Math.sin(anim * circleBounceSpeed.get()) * 0.5 + 0.5) * h : 0.05;
        int rings = circleLayers.get();

        if (circleGround.get()) ring(r, pos, radius, 0.02, col(friend, 0, 90), seg);
        for (int i = 0; i < rings; i++) {
            double yy = bounce ? y - i * circleSpacing.get() : y + i * circleSpacing.get();
            ring(r, pos, radius, yy, col(friend, i * 0.03, Fx.clampAlpha(255 - i * (200.0 / rings))), seg);
        }
    }

    private void ring(Renderer3D r, Vec3d pos, double radius, double y, Color c, int seg) {
        Vec3d last = null;
        for (int i = 0; i <= seg; i++) {
            double a = i / (double) seg * Math.PI * 2;
            Vec3d p = new Vec3d(pos.x + Math.cos(a) * radius, pos.y + y, pos.z + Math.sin(a) * radius);
            if (last != null) Fx.line(r, last, p, c);
            last = p;
        }
    }

    // ---------------------------------------------------------------- Ghosts

    private void ghosts(Renderer3D r, Vec3d pos, double w, double h, double anim, boolean friend) {
        int count = ghostCount.get();
        int trail = Math.max(4, (int) (26 * ghostLength.get()));

        // {thickness multiplier, alpha multiplier} per glow layer
        double[][] layers = switch (texture.get()) {
            case Soft -> new double[][]{{1, 1}};
            case Bloom -> new double[][]{{1, 1}, {2, 0.35}, {3.2, 0.15}};
            case Smoke -> new double[][]{{2, 0.4}, {3.5, 0.2}};
        };

        for (int g = 0; g < count; g++) {
            double base = anim * 2.2 + g * Math.PI * 2 / count;
            List<Vec3d> pts = new ArrayList<>();
            for (int k = 0; k <= trail; k++) {
                double a = base - k * 0.09;
                double rad = w * 0.9 + 0.25;
                double y;
                switch (path.get()) {
                    case Spiral -> {
                        double s = Math.sin(a * 0.5);
                        rad = w * 0.6 + 0.2 + (s * 0.5 + 0.5) * 0.4;
                        y = h * 0.5 + s * h * 0.45;
                    }
                    case Wave -> y = h * 0.5 + Math.sin(a * 3 + g) * h * 0.3;
                    default -> y = h * 0.5 + Math.sin(a + g) * h * 0.35;
                }
                pts.add(new Vec3d(pos.x + Math.cos(a) * rad, pos.y + y, pos.z + Math.sin(a) * rad));
            }

            final int n = trail;
            for (double[] layer : layers) {
                double half = 0.035 * ghostSize.get() * layer[0];
                double alphaMul = layer[1];
                final int gi = g;
                Fx.ribbon(r, pts, -half, half, i -> col(friend, gi * 0.2 + i / (double) n * 0.2,
                    Fx.clampAlpha(255 * alphaMul * (1 - i / (double) n))));
            }
        }
    }

    // -------------------------------------------------------------- Crystals

    private void crystals(Renderer3D r, Vec3d pos, double w, double h, double anim, boolean friend) {
        int n = crystalCount.get();
        for (int i = 0; i < n; i++) {
            double seed = i / (double) n;
            double t = (anim * crystalRise.get() + seed * 7.3) % 1.0;
            double a = seed * Math.PI * 2 * 3 + t * crystalTwist.get();
            double rad = w * crystalRadius.get() * (0.9 + 0.5 * Math.sin(seed * 20 + anim));
            double x = pos.x + Math.cos(a) * rad;
            double y = pos.y + t * h * crystalHeight.get();
            double z = pos.z + Math.sin(a) * rad;
            double s = crystalSize.get() * Math.sin(t * Math.PI);
            if (s <= 0.002) continue;

            Color c = col(friend, seed, Fx.clampAlpha(255 * Math.sin(t * Math.PI)));
            Fx.cube(r, x, y, z, s, c);
            if (crystalSparkles.get()) {
                Color arm = new Color(c.r, c.g, c.b, c.a / 2);
                r.line(x - s * 3, y, z, x + s * 3, y, z, arm);
                r.line(x, y - s * 3, z, x, y + s * 3, z, arm);
                r.line(x, y, z - s * 3, x, y, z + s * 3, arm);
            }
        }
    }

    // ------------------------------------------------------------- Lightning

    private void lightning(Renderer3D r, Vec3d pos, double w, double h, boolean friend) {
        long bucket = System.currentTimeMillis() / boltRefresh.get();   // new bolts every flicker-ms
        double jit = boltJitter.get();
        for (int b = 0; b < boltCount.get(); b++) {
            Random rnd = new Random(bucket * 31L + b);
            double sp = w * boltSpread.get();
            Vec3d from = pos.add((rnd.nextDouble() - 0.5) * sp, rnd.nextDouble() * h, (rnd.nextDouble() - 0.5) * sp);
            Vec3d prev = from;
            for (int i = 0; i < boltSegments.get(); i++) {
                Vec3d next = prev.add((rnd.nextDouble() - 0.5) * jit, (rnd.nextDouble() - 0.5) * jit, (rnd.nextDouble() - 0.5) * jit);
                Fx.line(r, prev, next, new Color(255, 255, 255, 255));
                if (boltGlow.get()) Fx.line(r, prev.add(0.02, 0, 0), next.add(0.02, 0, 0), col(friend, b * 0.15, 150));
                prev = next;
            }
        }
    }
}
