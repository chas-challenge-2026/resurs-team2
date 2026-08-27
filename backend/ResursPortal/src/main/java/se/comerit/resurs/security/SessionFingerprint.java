package se.comerit.resurs.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class SessionFingerprint {
    private final boolean useForwardedHeader;

    public SessionFingerprint(@Value("${session-token.use-forwarded-header:false}") boolean useForwardedHeader) {
        this.useForwardedHeader = useForwardedHeader;
    }

    public String of(HttpServletRequest request) {
        String ip = clientIp(request);
        String ua = request.getHeader("User-Agent");
        return (ua == null ? "" : ua) + "|" + (ip == null ? "" : ip);
    }

    /**
     * Parse out the client ip.
     * 
     * If forwarding is enabled then the ips are appended together
     * @param request http request
     * @return String representation of the IP
     */
    private String clientIp(HttpServletRequest request) {
        if (useForwardedHeader) {
            String fwd = request.getHeader("X-Forwarded-For");
            if (fwd != null && !fwd.isBlank()) {
                return fwd.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

}
