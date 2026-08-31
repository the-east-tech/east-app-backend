package com.eastapp.backend.config;

import com.eastapp.backend.activity.tracking.ActivityTrackingInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final ActivityTrackingInterceptor activityTrackingInterceptor;

    public WebConfiguration(ActivityTrackingInterceptor activityTrackingInterceptor) {
        this.activityTrackingInterceptor = activityTrackingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(activityTrackingInterceptor)
                .addPathPatterns("/api/v1/**");
    }
}
