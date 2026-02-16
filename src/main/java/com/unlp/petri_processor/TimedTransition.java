package com.unlp.petri_processor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class TimedTransition {

    private Long alpha;
    private Long beta;
    private Long enablingTime;
    private final Map<String, Long> uuidEnablingTime;

    public TimedTransition(Long alpha, Long beta) {
        this.alpha = alpha;
        this.beta = beta;
        this.enablingTime = System.currentTimeMillis();
        this.uuidEnablingTime = new HashMap<>();
    }

    public Long getAlpha() {
        return alpha;
    }

    public Long getBeta() {
        return beta;
    }

    public boolean canFire(Long currentTime) {
        return beta == Long.MAX_VALUE ? (enablingTime+alpha <= currentTime) : (enablingTime+alpha <= currentTime && enablingTime+beta >= currentTime);
    }

    public Long getEnablingTime(String uuid) {
        if (Objects.isNull(uuid)) {
            return enablingTime;
        }
        return uuidEnablingTime.get(uuid);
    }

    public void setEnablingTime(String uuid, Long enablingTime) {
        if (Objects.isNull(uuid)) {
            this.enablingTime = enablingTime;
        } else {
            uuidEnablingTime.put(uuid, enablingTime);
        }
    }
}
