package com.att.tdp.issueflow.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;

	private final UserRepository userRepository;

	private final TokenDenylistService tokenDenylistService;

	public JwtAuthenticationFilter(
			JwtService jwtService,
			UserRepository userRepository,
			TokenDenylistService tokenDenylistService) {
		this.jwtService = jwtService;
		this.userRepository = userRepository;
		this.tokenDenylistService = tokenDenylistService;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

		if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
			filterChain.doFilter(request, response);
			return;
		}

		String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
		if (token.isEmpty() || tokenDenylistService.isDenylisted(token) || !jwtService.isTokenValid(token)) {
			reject(response, "Invalid or expired bearer token");
			return;
		}

		try {
			String username = jwtService.extractUsername(token);
			User user = userRepository.findByUsernameIgnoreCase(username)
				.orElse(null);

			if (user == null) {
				reject(response, "Invalid bearer token");
				return;
			}

			if (SecurityContextHolder.getContext().getAuthentication() == null) {
				List<SimpleGrantedAuthority> authorities = List.of(
						new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
				UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
						user.getUsername(),
						null,
						authorities);
				authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
				SecurityContextHolder.getContext().setAuthentication(authentication);
			}
		}
		catch (RuntimeException exception) {
			SecurityContextHolder.clearContext();
			reject(response, "Invalid or expired bearer token");
			return;
		}

		filterChain.doFilter(request, response);
	}

	private void reject(HttpServletResponse response, String message) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}");
	}
}
