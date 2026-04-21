package com.husky.spring_portfolio.mvc.jwt;

/** JSON body for POST /authenticate (explicit shape for Jackson). */
public class AuthTokenResponse {

	private final String token;

	public AuthTokenResponse(String token) {
		this.token = token;
	}

	public String getToken() {
		return token;
	}
}
