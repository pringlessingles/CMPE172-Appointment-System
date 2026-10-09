package clinic.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import java.util.List;

public class AccountPrincipal extends User {
    private final long id;
    private final String role;
    public AccountPrincipal(long id, String email, String hash, String role) {
        super(email, hash, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        this.id = id;
        this.role = role;
    }
    public long id() { return id; }
    public String role() { return role; }
}
