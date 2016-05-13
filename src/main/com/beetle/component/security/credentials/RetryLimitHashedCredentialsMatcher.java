package com.beetle.component.security.credentials;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.ExcessiveAttemptsException;
import org.apache.shiro.authc.credential.HashedCredentialsMatcher;

import com.beetle.component.security.dto.SecUsers;
import com.beetle.component.security.service.SecurityServiceException;
import com.beetle.component.security.service.UserService;
import com.beetle.framework.AppProperties;
import com.beetle.framework.log.AppLogger;
import com.beetle.framework.resource.dic.DIContainer;

public class RetryLimitHashedCredentialsMatcher extends HashedCredentialsMatcher {
	private final UserService userService;
	private final int max;
	private static final AppLogger logger = AppLogger.getInstance(RetryLimitHashedCredentialsMatcher.class);

	public RetryLimitHashedCredentialsMatcher() {
		userService = DIContainer.getInstance().retrieve(UserService.class);
		this.max = AppProperties.getAsInt("security_login_time", 5);
	}

	@Override
	public boolean doCredentialsMatch(AuthenticationToken token, AuthenticationInfo info) {
		String username = (String) token.getPrincipal();
		// retry count + 1
		try {
			SecUsers user = userService.findByUsername(username);
			if (user == null) {
				// return false;
				throw new AuthenticationException("user not found!");
			}
			int curCount = user.getTrycount();
			if (curCount <= 0) {
				curCount = new AtomicInteger(0).get();
			}
			AtomicInteger retryCount = new AtomicInteger(curCount);
			if (retryCount.incrementAndGet() > max) {
				// if retry count > 5 throw
				logger.error("user:{},time:{}",user.getUsername(),user.getTrycount());
				throw new ExcessiveAttemptsException();
			}
			boolean matches = super.doCredentialsMatch(token, info);
			if (matches) {
				// clear retry count
				userService.updateTryTime(user.getUserId(), 0);
			} else {
				userService.updateTryTime(user.getUserId(), retryCount.get());
			}
			return matches;
		} catch (SecurityServiceException e) {
			logger.error(e);
			throw new AuthenticationException();
		}
	}

}
