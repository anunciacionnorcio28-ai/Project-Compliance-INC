package app.privmicro.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * CSRF defence for cookie auth: SameSite=Strict cookie plus a mandatory custom header on every state-changing
 * request (a cross-site form or image cannot set it).
 */
public class CsrfGuardFilter extends OncePerRequestFilter {
    private static final Set<String> UNSAFE = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (UNSAFE.contains(req.getMethod()) && !"XMLHttpRequest".equals(req.getHeader("X-Requested-With"))) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(req, res);
    }
}
