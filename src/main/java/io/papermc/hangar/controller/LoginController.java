package io.papermc.hangar.controller;

import io.papermc.hangar.config.hangar.HangarConfig;
import io.papermc.hangar.db.modelold.UsersTable;
import io.papermc.hangar.exceptions.HangarException;
import io.papermc.hangar.service.AuthenticationService;
import io.papermc.hangar.service.RoleService;
import io.papermc.hangar.service.SsoService;
import io.papermc.hangar.service.UserService;
import io.papermc.hangar.service.sso.AuthUser;
import io.papermc.hangar.service.sso.UrlWithNonce;
import io.papermc.hangar.util.AlertUtil;
import io.papermc.hangar.util.Routes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;

@Controller
public class LoginController extends HangarController {

    private final HangarConfig hangarConfig;
    private final AuthenticationService authenticationService;
    private final SsoService ssoService;
    private final UserService userService;
    private final RoleService roleService;

    @Autowired
    public LoginController(HangarConfig hangarConfig, AuthenticationService authenticationService, SsoService ssoService, UserService userService, RoleService roleService) {
        this.hangarConfig = hangarConfig;
        this.authenticationService = authenticationService;
        this.ssoService = ssoService;
        this.userService = userService;
        this.roleService = roleService;
    }

    @GetMapping("/login")
    public ModelAndView login(@RequestParam(defaultValue = "") String sso, @RequestParam(defaultValue = "") String sig, @RequestParam(defaultValue = "") String returnUrl, @CookieValue(value = "url", required = false) String redirectUrl, RedirectAttributes attributes) {
        if (hangarConfig.fakeUser.isEnabled()) {
            hangarConfig.checkDebug();

            UsersTable fakeUser = authenticationService.loginAsFakeUser();

            return redirectBack(returnUrl, fakeUser);
        } else if (sso.isEmpty()) {
            String returnPath = returnUrl.isBlank() ? request.getRequestURI() : returnUrl;
            try {
                response.addCookie(new Cookie("url", returnPath));
                return redirectToSso(ssoService.getLoginUrl(hangarConfig.getBaseUrl() + "/login"), attributes);
            } catch (HangarException e) {
                AlertUtil.showAlert(attributes, AlertUtil.AlertType.ERROR, e.getMessageKey(), e.getArgs());
                return Routes.SHOW_HOME.getRedirect();
            }

        } else {
            AuthUser authUser = ssoService.authenticate(sso, sig);
            if (authUser == null) {
                AlertUtil.showAlert(attributes, AlertUtil.AlertType.ERROR, "error.loginFailed");
                return Routes.SHOW_HOME.getRedirect();
            }

            UsersTable user = userService.getOrCreate(authUser.getUsername(), authUser);
            roleService.removeAllGlobalRoles(user.getId());
            authUser.getGlobalRoles().forEach(role -> roleService.addGlobalRole(user.getId(), role.getRoleId()));
            authenticationService.setAuthenticatedUser(user);

            String redirectPath = redirectUrl != null ? redirectUrl : Routes.getRouteUrlOf("showHome");
            return redirectBack(redirectPath, user);
        }
    }

    @PostMapping("/verify")
    public ModelAndView verify(@RequestParam String returnPath, RedirectAttributes attributes) {
        try {
            return redirectToSso(ssoService.getVerifyUrl(hangarConfig.getBaseUrl() + returnPath), attributes);
        } catch (HangarException e) {
            AlertUtil.showAlert(attributes, AlertUtil.AlertType.ERROR, e.getMessageKey(), e.getArgs());
            return Routes.SHOW_HOME.getRedirect();
        }
    }

    @GetMapping("/logout")
    public ModelAndView logout(HttpSession session) {
        // TODO flash
        session.invalidate();
        return Routes.getRedirectToUrl(hangarConfig.getAuthUrl() + "/accounts/logout/");
    }

    @GetMapping("/signup")
    public ModelAndView signUp(@RequestParam(defaultValue = "") String returnUrl, RedirectAttributes attributes) {
        try {
            return redirectToSso(ssoService.getSignupUrl(returnUrl), attributes);
        } catch (HangarException e) {
            AlertUtil.showAlert(attributes, AlertUtil.AlertType.ERROR, e.getMessageKey(), e.getArgs());
            return Routes.SHOW_HOME.getRedirect();
        }
    }

    private ModelAndView redirectBack(String url, UsersTable user) {
        if (!url.startsWith("http")) {
            if (url.startsWith("/")) {
                url = hangarConfig.getBaseUrl() + url;
            } else {
                url = hangarConfig.getBaseUrl() + "/" + url;
            }
        }
        return Routes.getRedirectToUrl(url);
    }

    private ModelAndView redirectToSso(UrlWithNonce urlWithNonce, RedirectAttributes attributes) {
        if (!hangarConfig.sso.isEnabled()) {
            AlertUtil.showAlert(attributes, AlertUtil.AlertType.ERROR, "error.noLogin");
            return Routes.SHOW_HOME.getRedirect();
        }
        ssoService.insert(urlWithNonce.getNonce());
        return Routes.getRedirectToUrl(urlWithNonce.getUrl());
    }
}
