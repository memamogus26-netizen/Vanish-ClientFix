package com.vanishclient.util;

import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.function.IntFunction;

/** Small drawing helpers shared by the Vanish Client modules. Only uses Meteor's Renderer3D. */
public final class Fx {
    private Fx() {}

    public static Vec3d pos(Entity e) {
        return new Vec3d(e.getX(), e.getY(), e.getZ());
    }

    public static Vec3d lerp(Vec3d a, Vec3d b, double t) {
        return new Vec3d(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }

    public static int clampAlpha(double a) {
        return (int) Math.max(0, Math.min(255, a));
    }

    /** Rainbow colour, speed in cycles per second. */
    public static Color rainbow(double speed, double offset, int alpha) {
        double h = ((System.currentTimeMillis() % 100000L) / 1000.0 * speed + offset) % 1.0;
        if (h < 0) h += 1;
        double x = h * 6;
        int i = (int) x;
        double f = x - i;
        double q = 1 - f;
        double r, g, b;
        switch (i % 6) {
            case 0 -> { r = 1; g = f; b = 0; }
            case 1 -> { r = q; g = 1; b = 0; }
            case 2 -> { r = 0; g = 1; b = f; }
            case 3 -> { r = 0; g = q; b = 1; }
            case 4 -> { r = f; g = 0; b = 1; }
            default -> { r = 1; g = 0; b = q; }
        }
        return new Color((int) (r * 255), (int) (g * 255), (int) (b * 255), alpha);
    }

    public static Color mix(Color a, Color b, double t, int alpha) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
            (int) (a.r + (b.r - a.r) * t),
            (int) (a.g + (b.g - a.g) * t),
            (int) (a.b + (b.b - a.b) * t),
            alpha);
    }

    public static void line(Renderer3D r, Vec3d a, Vec3d b, Color c) {
        r.line(a.x, a.y, a.z, b.x, b.y, b.z, c);
    }

    /** Vertical quad between two points, drawn from both sides so it never disappears when viewed from behind. */
    public static void quad(Renderer3D r, Vec3d a, Vec3d b, double yBottom, double yTop, Color c) {
        double ax = a.x, az = a.z, bx = b.x, bz = b.z;
        r.side(ax, a.y + yBottom, az, ax, a.y + yTop, az, bx, b.y + yTop, bz, bx, b.y + yBottom, bz, c, c, ShapeMode.Sides);
        r.side(bx, b.y + yBottom, bz, bx, b.y + yTop, bz, ax, a.y + yTop, az, ax, a.y + yBottom, az, c, c, ShapeMode.Sides);
    }

    /** Vertical ribbon through the points. colorAt(i) gives the colour of the segment i -> i+1. */
    public static void ribbon(Renderer3D r, List<Vec3d> pts, double yBottom, double yTop, IntFunction<Color> colorAt) {
        for (int i = 0; i < pts.size() - 1; i++) {
            Color c = colorAt.apply(i);
            if (c == null || c.a <= 0) continue;
            quad(r, pts.get(i), pts.get(i + 1), yBottom, yTop, c);
        }
    }

    public static void cube(Renderer3D r, double x, double y, double z, double s, Color c) {
        r.box(x - s, y - s, z - s, x + s, y + s, z + s, c, c, ShapeMode.Sides, 0);
    }
}
