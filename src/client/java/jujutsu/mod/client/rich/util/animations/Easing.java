package jujutsu.mod.client.rich.util.animations;

@FunctionalInterface
public interface Easing {
    double ease(double value);
}