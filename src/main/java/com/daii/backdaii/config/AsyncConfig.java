package com.daii.backdaii.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// Class-based proxies, so @Async beans can still expose methods that are not in their interface
// (e.g. SmtpEmailService's startup check).
@Configuration
@EnableAsync(proxyTargetClass = true)
public class AsyncConfig {
}
