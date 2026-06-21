package com.mythicrealm.api.gameplay.admin;

import com.mythicrealm.api.gameplay.auth.AuthenticatedAccount;
import com.mythicrealm.api.gameplay.auth.SessionService;
import com.mythicrealm.api.gameplay.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AdminAccessService {
    private final SessionService sessionService;
    private final AdminProperties adminProperties;

    public AdminAccessService(SessionService sessionService, AdminProperties adminProperties) {
        this.sessionService = sessionService;
        this.adminProperties = adminProperties;
    }

    public boolean isAdmin(AuthenticatedAccount account) {
        return account != null && adminProperties.isAdminUsername(account.username());
    }

    public AuthenticatedAccount requireAdmin(String authorizationHeader) {
        AuthenticatedAccount account = sessionService.require(authorizationHeader);
        if (!isAdmin(account)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }
        return account;
    }
}
