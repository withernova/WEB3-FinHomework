package com.maka.component;

import com.maka.utils.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class WebInterceptor implements HandlerInterceptor {
    private Logger logger = LoggerFactory.getLogger("WebInterceptor");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("token");
        logger.info("____{}_____",token);
        if(!( handler instanceof HandlerMethod)){
            return true;
        }
        if(token!=null){
            String username = JwtUtil.getUserNameByToken(token);
            Boolean result = JwtUtil.verifyToken(token, username, JwtUtil.SECRET);
            if(Boolean.TRUE.equals(result)){
                logger.info("success");
            }

        }
        return true;
    }
}
