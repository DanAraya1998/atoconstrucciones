package com.daraya.atoconstrucciones.publication;
import com.daraya.atoconstrucciones.shared.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
@Component @Order(-110)
public class ContactProtectionFilter extends OncePerRequestFilter {
    private final ContactRateLimiter limiter;private final JsonMapper json;private final String origin;private final boolean trustedTunnel;
    public ContactProtectionFilter(ContactRateLimiter limiter,JsonMapper json,@Value("${ato.web-origin}")String origin,@Value("${ato.contact.trusted-tunnel:false}")boolean trustedTunnel){this.limiter=limiter;this.json=json;this.origin=origin;this.trustedTunnel=trustedTunnel;}
    @Override protected boolean shouldNotFilter(HttpServletRequest r){return !r.getRequestURI().equals("/api/v1/public/contact")||!r.getMethod().equals("POST");}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
        try{
            String source=request.getRemoteAddr();
            // Solo activar en el origen loopback exclusivo del túnel. No confiar en X-Forwarded-For.
            if(trustedTunnel){if(!source.equals("127.0.0.1")&&!source.equals("0:0:0:0:0:0:0:1")&&!source.equals("::1"))throw ApiException.forbidden();String cf=request.getHeader("CF-Connecting-IP");if(cf!=null&&cf.matches("[0-9a-fA-F:.]{3,45}"))source=cf;}
            limiter.check(source);
            if(!origin.equals(request.getHeader("Origin")))throw new ApiException(403,"CONTACT_ORIGIN_INVALID","Origen de formulario no autorizado.");
            if(request.getContentType()==null||!request.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("application/json"))throw new ApiException(415,"UNSUPPORTED_MEDIA_TYPE","Use application/json.");
            byte[] body=request.getInputStream().readNBytes(16385);if(body.length>16384)throw new ApiException(413,"CONTACT_TOO_LARGE","El formulario excede el tamaño permitido.");
            chain.doFilter(new HttpServletRequestWrapper(request){
                @Override public ServletInputStream getInputStream(){var input=new ByteArrayInputStream(body);return new ServletInputStream(){public int read(){return input.read();}public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}};}
                @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8));}
            },response);
        }catch(ApiException e){response.setStatus(e.status());response.setContentType("application/problem+json");response.setHeader("Cache-Control","no-store");if(e.status()==429)response.setHeader("Retry-After","600");response.getWriter().write(json.writeValueAsString(ApiErrors.problem(e.status(),e.code(),e.getMessage(),request.getRequestURI())));}
    }
}
