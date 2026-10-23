package com.atrum.agrum.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    // "Today" for attendance is the estates' date, not the server's
    @Bean
    public Clock clock(@Value("${agrum.time-zone:Asia/Colombo}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
