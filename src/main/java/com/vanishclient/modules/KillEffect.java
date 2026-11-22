package com.vanishclient.modules;

import com.vanishclient.VanishClientAddon;
import com.vanishclient.util.Fx;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class KillEffect extends Module {
    public enum Type { Death, Particles, Thunder, Blood }
    public enum TargetType { Players, Mobs, Both }
    public enum Sound { None, Thunder, Orb, LevelUp }
    public enum ColorMode { Static, Rainbow }

    private final SettingGroup sgEffect = settings.getDefaultGroup();
    private final SettingGroup sgAppearance = settings.createGroup("Appearance");
    private final SettingGroup sgBehavior = settings.createGroup("Behavior");
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgSound = settings.createGroup("Sound");

    // ---- Effect type ----
    private final Setting<Type> effectType = sgEffect.add(new EnumSetting.Builder<Type>()
        .name("effect-type").description("Death = the entity dissolves into rising particles with a shockwave ring.")
        .defaultValue(Type.Death).build());

    private final Setting<TargetType> targetType = sgEffect.add(new EnumSetting.Builder<TargetType>()
        .name("target-type").description("Which kinds of entities show an effect when they die.")
        .defaultValue(TargetType.Both).build());

    private final Setting<Set<EntityType<?>>> entities = sgEffect.add(new EntityTypeListSetting.Builder()
        .name("entities").description("Select which entities to show effects for.")
        .defaultValue(EntityType.PLAYER, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER)
        .onlyAttackable().build());

    private final Setting<Double> maxDistance = sgEffect.add(new DoubleSetting.Builder()
        .name("max-distance").description("Ignore deaths farther away than this.")
        .defaultValue(64).min(8).sliderMax(200).build());

    // ---- Appearance ----
    private final Setting<Integer> count = sgAppearance.add(new IntSetting.Builder()
        .name("particle-count").description("Amount of particles per kill.")
        .defaultValue(40).min(5).sliderMax(150).build());

    private final Setting<Double> size = sgAppearance.add(new DoubleSetting.Builder()
        .name("particle-size").description("Particle size in blocks.")
        .defaultValue(0.06).min(0.01).sliderMax(0.25).build());

    private final Setting<Double> boltHeight = sgAppearance.add(new DoubleSetting.Builder()
        .name("thunder-height").description("How high the lightning bolt starts.")
        .defaultValue(24).min(5).sliderMax(60).build());

    private final Setting<Boolean> ring = sgAppearance.add(new BoolSetting.Builder()
        .name("shockwave-ring").description("Expanding ring on the ground for the Death effect.")
        .defaultValue(true).build());

    // ---- Behavior ----
    private final Setting<Double> duration = sgBehavior.add(new DoubleSetting.Builder()
        .name("duration").description("How long an effect lasts, in seconds.")
        .defaultValue(1.2).min(0.2).sliderMax(4).build());

    private final Setting<Double> spread = sgBehavior.add(new DoubleSetting.Builder()
        .name("spread").description("How fast particles fly outward.")
        .defaultValue(2.5).min(0.2).sliderMax(8).build());

    // ---- Colors ----
    private final Setting<ColorMode> colorMode = sgColors.add(new EnumSetting.Builder<ColorMode>()
        .name("color-mode").description("Colour for Death, Particles and Thunder.")
        .defaultValue(ColorMode.Static).build());

    private final Setting<SettingColor> color = sgColors.add(new ColorSetting.Builder()
        .name("color").description("Death, particle and thunder colour.")
        .defaultValue(new SettingColor(255, 140, 40, 255))
        .visible(() -> colorMode.get() == ColorMode.Static).build());

    private final Setting<SettingColor> bloodColor = sgColors.add(new ColorSetting.Builder()
        .name("blood-color").description("Colour of the Blood effect.")
        .defaultValue(new SettingColor(200, 10, 10, 255)).build());

    // ---- Sound ----
    private final Setting<Sound> sound = sgSound.add(new EnumSetting.Builder<Sound>()
        .name("sound").description("Sound played on a kill.")
        .defaultValue(Sound.None).build());

    private final Setting<Double> volume = sgSound.add(new DoubleSetting.Builder()
        .name("volume").description("Sound volume.")
        .defaultValue(0.8).min(0).max(2).sliderMax(2)
        .visible(() -> sound.get() != Sound.None).build());

    private static final class Burst {
        Type type;
        Vec3d pos;
        long start;
        long seed;
        double w, h;      // victim size
        double[][] org;   // per particle: start x, y, z
        double[][] vel;   // per particle: vx, vy, vz
        double[] hue;     // per particle colour offset
    }

    private final List<Burst> bursts = new ArrayList<>();
    private final Set<Integer> handled = new HashSet<>();
    private final Random rng = new Random();

    public KillEffect() {
        super(VanishClientAddon.CATEGORY, "kill-effect", "Shows an effect whenever an entity dies.");
    }

    @Override
    public void onDeactivate() {
        bursts.clear();
        handled.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) { bursts.clear(); handled.clear(); return; }

        long now = System.currentTimeMillis();

        // Any entity that reaches 0 health shows the effect, no matter who killed it.
        for (Entity ent : mc.world.getEntities()) {
            if (!(ent instanceof LivingEntity le) || le == mc.player) continue;
            if (le.getHealth() > 0f) { handled.remove(le.getId()); continue; }
            if (!handled.add(le.getId())) continue;
            if (!isAllowed(le) || mc.player.distanceTo(le) > maxDistance.get()) continue;
            spawn(effectType.get(), le);
        }
        handled.removeIf(id -> mc.world.getEntityById(id) == null);

        bursts.removeIf(b -> now - b.start > duration.get() * 1000);
    }

    private boolean isAllowed(LivingEntity le) {
        boolean player = le instanceof PlayerEntity;
        if (targetType.get() == TargetType.Players && !player) return false;
        if (targetType.get() == TargetType.Mobs && player) return false;
        return entities.get().contains(le.getType());
    }

    private void spawn(Type type, LivingEntity le) {
        Vec3d feet = Fx.pos(le);
        double w = le.getWidth(), h = le.getHeight();

        Burst b = new Burst();
        b.type = type;
        b.w = w;
        b.h = h;
        b.pos = feet.add(0, h * 0.5, 0);
        b.start = System.currentTimeMillis();
        b.seed = rng.nextLong();

        int n = type == Type.Thunder ? 0 : (type == Type.Blood ? Math.max(8, count.get() / 2) : count.get());
        b.org = new double[n][3];
        b.vel = new double[n][3];
        b.hue = new double[n];
        for (int i = 0; i < n; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            if (type == Type.Death) {
                // start somewhere inside the body, drift up and slightly outward
                b.org[i][0] = feet.x + (rng.nextDouble() - 0.5) * w;
                b.org[i][1] = feet.y + rng.nextDouble() * h;
                b.org[i][2] = feet.z + (rng.nextDouble() - 0.5) * w;
                double sideways = (0.1 + rng.nextDouble() * 0.3) * spread.get() * 0.3;
                b.vel[i][0] = Math.cos(a) * sideways;
                b.vel[i][1] = (0.5 + rng.nextDouble()) * spread.get() * 0.4;
                b.vel[i][2] = Math.sin(a) * sideways;
            } else {
                b.org[i][0] = b.pos.x;
                b.org[i][1] = b.pos.y;
                b.org[i][2] = b.pos.z;
                double up = type == Type.Blood ? 0.8 + rng.nextDouble() * 1.4 : (rng.nextDouble() - 0.3) * 1.6;
                double s = spread.get() * (0.3 + rng.nextDouble() * 0.7) * (type == Type.Blood ? 0.5 : 0.6);
                b.vel[i][0] = Math.cos(a) * s;
                b.vel[i][1] = up * (type == Type.Blood ? 2.2 : spread.get() * 0.4);
                b.vel[i][2] = Math.sin(a) * s;
            }
            b.hue[i] = rng.nextDouble();
        }
        bursts.add(b);
        playSound();
    }

    private void playSound() {
        SoundEvent ev = switch (sound.get()) {
            case None -> null;
            case Thunder -> SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER;
            case Orb -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
            case LevelUp -> SoundEvents.ENTITY_PLAYER_LEVELUP;
        };
        if (ev != null) mc.getSoundManager().play(PositionedSoundInstance.master(ev, 1f, volume.get().floatValue()));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (bursts.isEmpty()) return;
        long now = System.currentTimeMillis();
        double dur = duration.get() * 1000;

        for (Burst b : bursts) {
            double age = now - b.start;
            double life = Math.min(1, age / dur);
            double t = age / 1000.0;
            int alpha = Fx.clampAlpha(255 * (1 - life));

            switch (b.type) {
                case Death -> {
                    for (int i = 0; i < b.vel.length; i++) {
                        double x = b.org[i][0] + b.vel[i][0] * t;
                        double y = b.org[i][1] + b.vel[i][1] * t;
                        double z = b.org[i][2] + b.vel[i][2] * t;
                        // twinkle while fading out
                        double twinkle = 0.75 + 0.25 * Math.sin(t * 14 + b.hue[i] * 40);
                        Fx.cube(event.renderer, x, y, z, size.get() * twinkle * (1 - life * 0.5), particleColor(b.hue[i], alpha));
                    }
                    if (ring.get()) {
                        Vec3d feet = new Vec3d(b.pos.x, b.pos.y - b.h / 2, b.pos.z);
                        double rad = b.w * 0.6 + life * 2.4;
                        ring(event, feet, rad, 0.05, particleColor(0, Fx.clampAlpha(alpha * 0.9)));
                        ring(event, feet, rad * 0.8, 0.12, particleColor(0.2, Fx.clampAlpha(alpha * 0.5)));
                    }
                }
                case Particles -> {
                    for (int i = 0; i < b.vel.length; i++) {
                        double x = b.org[i][0] + b.vel[i][0] * t;
                        double y = b.org[i][1] + b.vel[i][1] * t - 2.0 * t * t;
                        double z = b.org[i][2] + b.vel[i][2] * t;
                        Fx.cube(event.renderer, x, y, z, size.get() * (1 - life * 0.6), particleColor(b.hue[i], alpha));
                    }
                }
                case Blood -> {
                    Color c = bloodColor.get();
                    for (int i = 0; i < b.vel.length; i++) {
                        double x = b.org[i][0] + b.vel[i][0] * t;
                        double y = Math.max(b.pos.y - 1.0, b.org[i][1] + b.vel[i][1] * t - 9.0 * t * t);
                        double z = b.org[i][2] + b.vel[i][2] * t;
                        double shade = 0.6 + 0.4 * b.hue[i];
                        Fx.cube(event.renderer, x, y, z, size.get() * 1.4,
                            new Color((int) (c.r * shade), (int) (c.g * shade), (int) (c.b * shade), Fx.clampAlpha(255 * (1 - life * life))));
                    }
                }
                case Thunder -> drawBolt(event, b, alpha);
            }
        }
    }

    private void drawBolt(Render3DEvent event, Burst b, int alpha) {
        Random r = new Random(b.seed);
        Vec3d top = b.pos.add(0, boltHeight.get(), 0);
        int segs = 14;
        Vec3d prev = top;
        for (int i = 1; i <= segs; i++) {
            double f = i / (double) segs;
            Vec3d next = i == segs ? b.pos
                : new Vec3d(
                    top.x + (b.pos.x - top.x) * f + (r.nextDouble() - 0.5) * 1.6,
                    top.y + (b.pos.y - top.y) * f,
                    top.z + (b.pos.z - top.z) * f + (r.nextDouble() - 0.5) * 1.6);
            Color core = new Color(255, 255, 255, alpha);
            Color glow = particleColor(f, Fx.clampAlpha(alpha * 0.6));
            Fx.line(event.renderer, prev, next, core);
            for (double o : new double[]{0.04, -0.04}) {
                Fx.line(event.renderer, prev.add(o, 0, 0), next.add(o, 0, 0), glow);
                Fx.line(event.renderer, prev.add(0, 0, o), next.add(0, 0, o), glow);
            }
            prev = next;
        }
    }

    private void ring(Render3DEvent event, Vec3d center, double radius, double y, Color c) {
        int seg = 40;
        Vec3d last = null;
        for (int i = 0; i <= seg; i++) {
            double a = i / (double) seg * Math.PI * 2;
            Vec3d p = new Vec3d(center.x + Math.cos(a) * radius, center.y + y, center.z + Math.sin(a) * radius);
            if (last != null) Fx.line(event.renderer, last, p, c);
            last = p;
        }
    }

    private Color particleColor(double hue, int alpha) {
        if (colorMode.get() == ColorMode.Rainbow) return Fx.rainbow(0.5, hue, alpha);
        SettingColor c = color.get();
        return new Color(c.r, c.g, c.b, alpha);
    }
}
