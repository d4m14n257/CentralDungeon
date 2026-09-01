package com.centraldungeon.health;

import com.centraldungeon.health.dto.HealthResponse;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    public HealthResponse checkStatus() {
        return new HealthResponse("UP");
    }
}
