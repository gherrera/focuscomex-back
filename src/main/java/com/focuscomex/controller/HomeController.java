package com.focuscomex.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class HomeController {

	
	@GetMapping("/")
	@ResponseBody
	public String home() {		
		return "API home";
	}
	
}