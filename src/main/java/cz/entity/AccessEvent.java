package cz.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "access_events")
public class AccessEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String ipAddress;

    @Column(length = 250)
    private String forwardedFor;

    @Column(length = 300)
    private String userAgent;

    @Column(length = 300)
    private String detail;

    public enum Type {
        REGISTRATION("Registrace"),
        EMAIL_CODE_SENT("Ověřovací kód"),
        EMAIL_VERIFIED("E-mail ověřen"),
        LOGIN_SUCCESS("Přihlášení"),
        LOGIN_FAILURE("Neúspěšné přihlášení"),
        LOGOUT("Odhlášení"),
        USER_BLOCKED("Zablokování"),
        USER_UNBLOCKED("Odblokování");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getForwardedFor() { return forwardedFor; }
    public void setForwardedFor(String forwardedFor) { this.forwardedFor = forwardedFor; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
