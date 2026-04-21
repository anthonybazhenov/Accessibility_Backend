package com.husky.spring_portfolio.mvc.jwt;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.husky.spring_portfolio.mvc.person.PersonDetailsService;

@RestController
@CrossOrigin
public class JwtApiController {

	private static final Logger log = LoggerFactory.getLogger(JwtApiController.class);

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private JwtTokenUtil jwtTokenUtil;

	@Autowired
	private PersonDetailsService personDetailsService;

	@PostMapping(value = "/authenticate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> createAuthenticationToken(@RequestBody LoginRequest authenticationRequest) throws Exception {
		String username = authenticationRequest != null ? authenticationRequest.getUsername() : null;
		String password = authenticationRequest != null ? authenticationRequest.getPassword() : null;
		if (username == null || username.isBlank() || password == null) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("error", "INVALID_CREDENTIALS"));
		}
		try {
			authenticate(username.trim(), password);
		} catch (Exception e) {
			if (e.getMessage() != null && e.getMessage().contains("INVALID_CREDENTIALS")) {
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
						.body(Map.of("error", "INVALID_CREDENTIALS"));
			}
			throw e;
		}
		final UserDetails userDetails = personDetailsService.loadUserByUsername(username.trim());
		final String token = jwtTokenUtil.generateToken(userDetails);
		log.info("POST /authenticate succeeded for user {}", userDetails.getUsername());
		// SameSite=None requires Secure; browsers treat localhost as secure for dev
		final ResponseCookie tokenCookie = ResponseCookie.from("jwt", token)
			.httpOnly(true)
			.secure(true)
			.path("/")
			.maxAge(3600)
			.sameSite("None")
			.build();
		// JSON body + exposed header so SPA can read token when cross-origin cookies fail
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, tokenCookie.toString())
			.header("X-JWT-Token", token)
			.body(new AuthTokenResponse(token));
	}

	private void authenticate(String username, String password) throws Exception {
		try {
			authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
		} catch (BadCredentialsException e) {
			throw new Exception("INVALID_CREDENTIALS", e);
		} catch (Exception e) {
			throw new Exception(e);
		}
	}
}