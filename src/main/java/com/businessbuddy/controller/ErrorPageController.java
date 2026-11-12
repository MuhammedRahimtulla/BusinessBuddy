package com.businessbuddy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** Target of the access-denied handler; renders the 403 page. */
@Controller
public class ErrorPageController {

    @RequestMapping("/error/403")
    public String forbidden() {
        return "error/403";
    }
}
