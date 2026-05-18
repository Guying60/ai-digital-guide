package com.guying.config;

import com.guying.interceptor.AdminJwtInterceptor;
import com.guying.interceptor.UserJwtInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private UserJwtInterceptor userJwtInterceptor;
    @Autowired
    private AdminJwtInterceptor adminJwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(userJwtInterceptor)
                .addPathPatterns("/v1/users/**")
                .excludePathPatterns("/v1/users/login", "/v1/users/register", "/swagger-ui/**", "/v3/api-docs/**");

        registry.addInterceptor(adminJwtInterceptor)
                .addPathPatterns("/v1/admins/**")
                .excludePathPatterns("/v1/admins/login",  "/v1/admins/register", "/swagger-ui/**", "/v3/api-docs/**");
    }
}