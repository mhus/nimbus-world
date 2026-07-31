package de.mhus.nimbus.world.shared.access;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;

/**
 * Aspect for processing access control annotations on REST endpoints.
 *
 * This aspect intercepts REST controller methods annotated with:
 * - @RequireWorldRole: Requires specific world role
 * - @RequireSectorRole: Requires specific sector role
 * - @RequireAgent: Requires agent authentication
 * - @RequireSession: Requires session authentication
 * - @RequireWorldIsNotInstance: Requires world to not be an instance
 * - @RequireRegionMaintainer: Requires the user to be a region maintainer
 *
 * If access is denied, returns HTTP 403 Forbidden.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AccessControlAspect {

    private final AccessValidator accessUtil;

    /**
     * Intercepts all REST controller methods to check access annotations.
     */
    @Around("@annotation(org.springframework.web.bind.annotation.RequestMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.GetMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PatchMapping)")
    public Object checkAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Get current HTTP request
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            // Fail-closed: without a request context we cannot evaluate access annotations.
            log.warn("No request attributes found - denying access (fail-closed)");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No request context available for access control");
        }

        HttpServletRequest request = attributes.getRequest();

        // Skip access control for universe-to-sector requests (authenticated via ControlAccessFilter)
        Object userId = request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        if (userId instanceof String uid && uid.startsWith("universe:")) {
            return joinPoint.proceed();
        }

        // Skip access control for dev-mode full-access sessions. This attribute is only ever set by
        // ControlAccessFilter when dev-login is enabled and the dev-login key was presented as a
        // Bearer token — inert in production (dev-login disabled).
        if (Boolean.TRUE.equals(request.getAttribute(AccessFilterBase.ATTR_DEV_FULL_ACCESS))) {
            log.debug("Dev full-access bypass for {}", request.getRequestURI());
            return joinPoint.proceed();
        }

        // Skip access control for public asset paths (p: and rp: prefixes)
        String requestUri = request.getRequestURI();
        if (requestUri != null && requestUri.matches(".*/assets/(p|rp):.*")) {
            return joinPoint.proceed();
        }

        // Check class-level annotations first
        Class<?> declaringClass = method.getDeclaringClass();
        String accessDeniedReason = checkClassAnnotations(declaringClass, request);

        // Check method-level annotations (override class-level)
        if (accessDeniedReason == null) {
            accessDeniedReason = checkMethodAnnotations(method, request);
        }

        // If access denied, respond 403 Forbidden regardless of the handler's return type.
        // Throwing (instead of returning a ResponseEntity) avoids a ClassCastException in the
        // AOP proxy for handlers that declare a non-ResponseEntity return type.
        if (accessDeniedReason != null) {
            log.warn("Access denied: {} - method: {}.{}",
                    accessDeniedReason,
                    method.getDeclaringClass().getSimpleName(),
                    method.getName());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, accessDeniedReason);
        }

        // Access granted - proceed with method execution
        return joinPoint.proceed();
    }

    /**
     * Checks class-level annotations.
     */
    private String checkClassAnnotations(Class<?> clazz, HttpServletRequest request) {
        if (clazz.isAnnotationPresent(RequireWorldRole.class)) {
            RequireWorldRole annotation = clazz.getAnnotation(RequireWorldRole.class);
            if (!accessUtil.hasWorldRole(request, annotation.value())) {
                return "Missing required world role: " + annotation.value();
            }
        }

        if (clazz.isAnnotationPresent(RequireSectorRole.class)) {
            RequireSectorRole annotation = clazz.getAnnotation(RequireSectorRole.class);
            if (!accessUtil.hasSectorRole(request, annotation.value())) {
                return "Missing required sector role: " + annotation.value();
            }
        }

        if (clazz.isAnnotationPresent(RequireAgent.class)) {
            if (!accessUtil.isAgent(request)) {
                return "Agent authentication required";
            }
        }

        if (clazz.isAnnotationPresent(RequireSession.class)) {
            if (!accessUtil.hasSession(request)) {
                return "Session authentication required";
            }
        }

        if (clazz.isAnnotationPresent(RequireWorldIsNotInstance.class)) {
            if (accessUtil.isWorldInstance(request)) {
                return "Operation not allowed on instance worlds";
            }
        }

        if (clazz.isAnnotationPresent(RequireRegionMaintainer.class)) {
            if (!accessUtil.isRegionMaintainer(request)) {
                return "Region maintainer access required";
            }
        }

        return null; // Access granted
    }

    /**
     * Checks method-level annotations.
     */
    private String checkMethodAnnotations(Method method, HttpServletRequest request) {
        if (method.isAnnotationPresent(RequireWorldRole.class)) {
            RequireWorldRole annotation = method.getAnnotation(RequireWorldRole.class);
            if (!accessUtil.hasWorldRole(request, annotation.value())) {
                return "Missing required world role: " + annotation.value();
            }
        }

        if (method.isAnnotationPresent(RequireSectorRole.class)) {
            RequireSectorRole annotation = method.getAnnotation(RequireSectorRole.class);
            if (!accessUtil.hasSectorRole(request, annotation.value())) {
                return "Missing required sector role: " + annotation.value();
            }
        }

        if (method.isAnnotationPresent(RequireAgent.class)) {
            if (!accessUtil.isAgent(request)) {
                return "Agent authentication required";
            }
        }

        if (method.isAnnotationPresent(RequireSession.class)) {
            if (!accessUtil.hasSession(request)) {
                return "Session authentication required";
            }
        }

        if (method.isAnnotationPresent(RequireWorldIsNotInstance.class)) {
            if (accessUtil.isWorldInstance(request)) {
                return "Operation not allowed on instance worlds";
            }
        }

        if (method.isAnnotationPresent(RequireRegionMaintainer.class)) {
            if (!accessUtil.isRegionMaintainer(request)) {
                return "Region maintainer access required";
            }
        }

        return null; // Access granted
    }
}
