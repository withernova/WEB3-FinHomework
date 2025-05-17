package com.maka.controller;


import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;

@CrossOrigin
@Controller
public class DataViewController {

    @RequestMapping("/data")
    public String moveDataView(){
        return "data-view.html";
    }


}
