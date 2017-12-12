package org.jon.session.controller;

import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Package: org.jon.session.controller.LoginController
 * Description: 描述
 * Copyright: Copyright (c) 2017
 *
 * @author lv bin
 * Date: 2017/12/7 14:04
 * Version: V1.0.0
 */

@Controller
@RequestMapping("")
public class LoginController {

    @RequestMapping("/")
    public String toLogin(){
        return "login";
    }

    @RequestMapping("/login")
    public String login(String username, String pwd, HttpServletRequest request,  HttpSession session){

        if(StringUtils.isEmpty(username)){
            return "login";
        }

        int port = request.getServerPort();
        session.setAttribute("username", username);
        session.setAttribute("serverPort", port);
        session.setAttribute("count", 0);
        return "index";
    }

    @RequestMapping("/buy")
    public String buy(HttpSession session){

       String username =  (String)session.getAttribute("username");
       if(StringUtils.isEmpty(username)){
            return "login";
        }

        int count = (int)session.getAttribute("count");
        count++;
        session.setAttribute("count", count);

        return "index";
    }
}
