package com.husky.spring_portfolio.mvc.jwt;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Body for POST /authenticate. Uses a dedicated type so password is deserialized from JSON
 * (Person.password is {@code WRITE_ONLY} to Jackson, so it is not read from login requests).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginRequest {

	/** Login id; JSON may use {@code "username"} (SPA) or {@code "email"} (legacy Thymeleaf page). */
	@JsonAlias("email")
	private String username;
	private String password;

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
}
