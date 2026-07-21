package jujutsu.mod.character.nobara.projectjjk;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public final class ServerTimeDilationTest {
	private ServerTimeDilationTest() {}

	public static void main(String[] args) throws Exception {
		Class<?> controllerType = Class.forName("jujutsu.mod.character.nobara.projectjjk.ServerTimeDilation");
		Class<?> accessType = Class.forName("jujutsu.mod.character.nobara.projectjjk.ServerTimeDilation$TickRateAccess");

		Object controller = controllerType.getDeclaredConstructor().newInstance();
		Method trigger = controllerType.getDeclaredMethod("trigger", accessType, float.class, int.class);
		Method tick = controllerType.getDeclaredMethod("tick", accessType);
		Method clear = controllerType.getDeclaredMethod("clear", accessType);
		FakeRate rate = new FakeRate(accessType, 20.0f);

		trigger.invoke(controller, rate.proxy(), 6.0f, 4);
		assert rate.current() == 6.0f : rate.current();
		for (int index = 0; index < 3; index++) {
			tick.invoke(controller, rate.proxy());
		}
		assert rate.current() == 6.0f : "slowdown must remain active for the whole configured window";
		tick.invoke(controller, rate.proxy());
		assert rate.current() == 20.0f : "slowdown must restore the prior server tick rate";

		trigger.invoke(controller, rate.proxy(), 8.0f, 2);
		trigger.invoke(controller, rate.proxy(), 6.0f, 4);
		for (int index = 0; index < 4; index++) {
			tick.invoke(controller, rate.proxy());
		}
		assert rate.current() == 20.0f : "overlapping Resonance must preserve the original restore rate";

		trigger.invoke(controller, rate.proxy(), 6.0f, 1);
		rate.setExternally(12.0f);
		tick.invoke(controller, rate.proxy());
		assert rate.current() == 12.0f : "manual tick-rate changes must not be overwritten on restore";

		rate.setExternally(20.0f);
		trigger.invoke(controller, rate.proxy(), 6.0f, 4);
		clear.invoke(controller, rate.proxy());
		assert rate.current() == 20.0f : "shutdown cleanup must restore the prior tick rate";
		System.out.println("ServerTimeDilationTest passed");
	}

	private static final class FakeRate {
		private final List<Float> writes = new ArrayList<>();
		private final Object proxy;
		private float current;

		private FakeRate(Class<?> accessType, float initial) {
			current = initial;
			proxy = Proxy.newProxyInstance(accessType.getClassLoader(), new Class<?>[] {accessType}, (ignored, method, args) -> {
				return switch (method.getName()) {
					case "tickRate" -> current;
					case "setTickRate" -> {
						current = (float) args[0];
						writes.add(current);
						yield null;
					}
					case "toString" -> "FakeRate[" + current + "]";
					case "hashCode" -> System.identityHashCode(this);
					case "equals" -> ignored == args[0];
					default -> throw new UnsupportedOperationException(method.toString());
				};
			});
		}

		private Object proxy() {
			return proxy;
		}

		private float current() {
			return current;
		}

		private void setExternally(float value) {
			current = value;
		}
	}
}
