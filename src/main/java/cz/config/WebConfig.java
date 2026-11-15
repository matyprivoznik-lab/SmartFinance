package cz.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SetupInterceptor setupInterceptor;
    private final PremiumInterceptor premiumInterceptor;

    public WebConfig(SetupInterceptor setupInterceptor, PremiumInterceptor premiumInterceptor) {
        this.setupInterceptor = setupInterceptor;
        this.premiumInterceptor = premiumInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(setupInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/favicon.ico", "/error");
        registry.addInterceptor(premiumInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/favicon.ico", "/error");
    }
}
