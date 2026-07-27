package cvt.cv.ppmbackend.exception;
import lombok.Getter; import org.springframework.http.HttpStatus; import java.util.Map;
@Getter public class DomainException extends RuntimeException { private final String code; private final HttpStatus status; private final Map<String,Object> details; public DomainException(HttpStatus s,String c,String m,Map<String,Object>d){super(m);status=s;code=c;details=d;} }
