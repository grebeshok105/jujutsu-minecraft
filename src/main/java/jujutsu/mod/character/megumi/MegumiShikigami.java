package jujutsu.mod.character.megumi;

/**
 * The Ten Shadows roster beyond the Divine Dogs: which shikigami Megumi's technique key currently
 * commands. {@link #DOGS} is the default and delegates to the untouched dog runtime; the remaining
 * constants are the shikigami this slice adds.
 */
public enum MegumiShikigami {
	DOGS("dogs"),
	NUE("nue"),
	TOAD("toad"),
	RABBITS("rabbits"),
	ELEPHANT("elephant"),
	SERPENT("serpent"),
	DEER("deer"),
	OX("ox"),
	TIGER("tiger");

	private final String id;

	MegumiShikigami(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	/** Cycle order wraps around: dogs → nue → toad → rabbits → elephant → serpent → deer → ox → tiger → dogs. */
	public MegumiShikigami next() {
		MegumiShikigami[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	public static MegumiShikigami byId(String id) {
		for (MegumiShikigami type : values()) {
			if (type.id.equals(id)) {
				return type;
			}
		}
		throw new IllegalArgumentException("unknown shikigami id: " + id);
	}
}