/**
 * Copyright (C) 2015 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.web.mvc.spring.security.authentication;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import com.serotonin.m2m2.db.dao.UserDao;
import com.serotonin.m2m2.vo.User;
import com.serotonin.m2m2.vo.permission.PermissionHolder;
import com.serotonin.m2m2.vo.role.Role;

/**
 * Class for plug-in User Access for Authentication Data
 * 
 * @author Terry Packer
 *
 */
@Component
public class MangoUserDetailsService implements UserDetailsService {

	/* (non-Javadoc)
	 * @see org.springframework.security.core.userdetails.UserDetailsService#loadUserByUsername(java.lang.String)
	 */
	@Override
	public User loadUserByUsername(String username) throws UsernameNotFoundException {
		User user = UserDao.getInstance().getUser(username);
		if (user == null)
		    throw new UsernameNotFoundException(username);
		else
			return user;
	}

	/**
	 * Create a set of Granted Authorities by parsing a set of roles
	 * 
	 * @param roles
	 * @return
	 */
	public static Set<GrantedAuthority> getGrantedAuthorities(Set<Role> roles) {
	    if (roles == null) {
	        return Collections.emptySet();
	    }

        Set<GrantedAuthority> grantedAuthorities = new HashSet<GrantedAuthority>(roles.size());
        
        for (Role role : roles) {
        	if(role.equals(PermissionHolder.SUPERADMIN_ROLE)) {
        		grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        	}else {
            	grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + role.getXid().toUpperCase(Locale.ROOT)));
        	}
        }
        
        return grantedAuthorities;
	}
}
