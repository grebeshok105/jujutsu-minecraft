package jujutsu.mod.client.rich.util.animations;

import jujutsu.mod.client.rich.util.timer.TimerUtil;

public class GuiAnimation {
	public final TimerUtil counter = new TimerUtil();
	protected int ms = 250;
	protected double value = 1.0;
	protected Direction direction = Direction.FORWARDS;

	public GuiAnimation reset() {
		counter.resetCounter();
		return this;
	}

	public GuiAnimation setMs(int ms) {
		this.ms = ms;
		return this;
	}

	public GuiAnimation setValue(double value) {
		this.value = value;
		return this;
	}

	public GuiAnimation setDirection(Direction direction) {
		this.direction = direction;
		return this;
	}

	public Direction getDirection() {
		return direction;
	}

	public boolean isDone() {
		return counter.isReached(ms);
	}

	public Double getOutput() {
		double progress = Math.min(1.0, (double) counter.getTime() / Math.max(1, ms));
		double eased = 1 - Math.pow(1 - progress, 4);
		if (direction == Direction.FORWARDS) {
			return eased * value;
		}
		return (1.0 - eased) * value;
	}
}
