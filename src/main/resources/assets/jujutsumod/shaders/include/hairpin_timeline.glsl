#ifndef JUJUTSUMOD_HAIRPIN_TIMELINE
#define JUJUTSUMOD_HAIRPIN_TIMELINE

float hairpin_snap_gate(float t) {
    return smoothstep(0.36, 0.40, t) * (1.0 - smoothstep(0.43, 0.48, t));
}

float hairpin_residue_gate(float t) {
    return smoothstep(0.50, 0.62, t) * (1.0 - smoothstep(0.92, 1.0, t));
}

float hairpin_edge_gate(float t) {
    return smoothstep(0.12, 0.18, t) * (1.0 - smoothstep(0.30, 0.38, t));
}

#endif
