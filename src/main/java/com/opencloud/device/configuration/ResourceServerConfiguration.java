package com.opencloud.device.configuration;

import com.opencloud.device.exception.BaseAccessDeniedHandler;
import com.opencloud.device.exception.BaseAuthenticationEntryPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.provider.authentication.BearerTokenExtractor;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;

/**
 * oauth2资源服务器配置
 *
 * @author: liuyadu
 * @date: 2018/10/23 10:31
 * @description:
 */
@Slf4j
@Configuration
@EnableResourceServer
public class ResourceServerConfiguration extends ResourceServerConfigurerAdapter {

    @Autowired
    private TokenStore tokenStore;

    private BearerTokenExtractor tokenExtractor = new BearerTokenExtractor();

    /**
     * 跨域配置
     *
     * @return
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(true);
        configuration.addAllowedOrigin("*");
        configuration.addAllowedHeader("*");
        configuration.addAllowedMethod("*");
        source.registerCorsConfiguration("/**", configuration);
        return source;

//        return (ServerWebExchange ctx, WebFilterChain chain) -> {
//            ServerHttpRequest request = ctx.getRequest();
//            if (CorsUtils.isCorsRequest(request)) {
//                HttpHeaders requestHeaders = request.getHeaders();
//                ServerHttpResponse response = ctx.getResponse();
//                HttpMethod requestMethod = requestHeaders.getAccessControlRequestMethod();
//                HttpHeaders headers = response.getHeaders();
//                headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, requestHeaders.getOrigin());
//                headers.addAll(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, requestHeaders.getAccessControlRequestHeaders());
//                if (requestMethod != null) {
//                    headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, requestMethod.name());
//                }
//                headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
//                headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "*");
//                headers.add(HttpHeaders.ACCESS_CONTROL_MAX_AGE, MAX_AGE);
//                if (request.getMethod() == HttpMethod.OPTIONS) {
//                    response.setStatusCode(HttpStatus.OK);
//                    return Mono.empty();
//                }
//            }
//            return chain.filter(ctx);
//        };
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .and()
                .authorizeRequests()
                .antMatchers("/login/**","/oauth/**","/file/**").permitAll()
                // 监控端点内部放行
//                .requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll()
                .anyRequest().authenticated()
                .and()
                .formLogin().loginPage("/login").permitAll()
                .and()
                .logout().permitAll()
                // /logout退出清除cookie
                .addLogoutHandler(new CookieClearingLogoutHandler("token", "remember-me"))
                .logoutSuccessHandler(new LogoutSuccessHandler())
                .and()
                // 认证鉴权错误处理,为了统一异常处理。每个资源服务器都应该加上。
                .exceptionHandling()
                .accessDeniedHandler(new BaseAccessDeniedHandler())
                .authenticationEntryPoint(new BaseAuthenticationEntryPoint())
                .and()
                .csrf().disable()
                .cors().and()
                // 禁用httpBasic
                .httpBasic().disable();
    }


    public class LogoutSuccessHandler extends SimpleUrlLogoutSuccessHandler {
        public LogoutSuccessHandler() {
            // 重定向到原地址
            this.setUseReferer(true);
        }

        @Override
        public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
            try {
                // 解密请求头
                authentication =  tokenExtractor.extract(request);
                if(authentication!=null && authentication.getPrincipal()!=null){
                    String tokenValue = authentication.getPrincipal().toString();
                    log.debug("revokeToken tokenValue:{}",tokenValue);
                    // 移除token
                    tokenStore.removeAccessToken(tokenStore.readAccessToken(tokenValue));
                }
            }catch (Exception e){
                log.error("revokeToken error:{}",e);
            }
            super.onLogoutSuccess(request, response, authentication);
        }
    }
}
