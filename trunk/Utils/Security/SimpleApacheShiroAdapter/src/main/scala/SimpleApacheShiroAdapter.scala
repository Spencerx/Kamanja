/*
 * Copyright 2015 ligaDATA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// An early implementation of authentication module using Apache-Shiro
// We have lot more work to do.
package com.ligadata.Security

import org.apache.shiro.SecurityUtils
import org.apache.shiro.authc._
import org.apache.shiro.config.IniSecurityManagerFactory
import org.apache.shiro.mgt.SecurityManager
import org.apache.shiro.session.Session
import org.apache.shiro.subject.Subject
import org.apache.shiro.util.Factory
import org.apache.logging.log4j._

import com.ligadata.SecurityAdapterInfo.SecurityAdapter
import java.util.Properties
import com.ligadata.Exceptions.StackTrace

class SimpleApacheShiroAdapter extends SecurityAdapter{

  /**
   * Simple thing for here.....  This impl treats all request as READ/WRITE
   */
  override def getPrivilegeName (op: String, objectName: String): String = {
    if (op.equalsIgnoreCase("get")) {
      return "read" 
    } else {
      return "write"
    }
  }
  
  /**
   * 
   */
  def init: Unit = {}

  /**
   * 
   */
  override def performAuth(secParams: java.util.Properties): Boolean = {

    val loggerName = this.getClass.getName
    val log = LogManager.getLogger(loggerName)
   // log.setLevel(Level.TRACE);
    
    val factory = new IniSecurityManagerFactory("classpath:shiro.ini");
    val securityManager = factory.getInstance();
    SecurityUtils.setSecurityManager(securityManager);

    val username = secParams.getProperty("userid")
    if( username == null ){
      log.error("SimpleApacheShiroAdapter: userid is not supplied: unable to authenticate")
      return false;
    }

    val password = secParams.getProperty("password")
    if( username == null ){
      log.error("SimpleApacheShiroAdapter: password is not supplied: unable to authenticate")
      return false;
    }

    val role = secParams.getProperty("role")
    val privilege = secParams.getProperty("privilige")

    if( role == null && privilege == null ){
      log.error("SimpleApacheShiroAdapter: Either role or privilege must be supplied: unable to authenticate")
      return false;
    }

    // get the currently executing user:
    val currentUser = SecurityUtils.getSubject();

    // let's login the current user so we can check against roles and permissions:
    if (!currentUser.isAuthenticated()) {
      log.debug("SimpleApacheShiroAdapter: running authentication again")
      val token = new UsernamePasswordToken(username,password);
     //token.setRememberMe(true);
      try {
        currentUser.login(token);
      } catch {
        case uae:UnknownAccountException => {
          log.error("SimpleApacheShiroAdapter: There is no user with username of " + token.getPrincipal());
          return false
        } 
        case ice:IncorrectCredentialsException => {
          
          log.error("SimpleApacheShiroAdapter: Password for account " + token.getPrincipal() + " was incorrect!");
          return false
        } 
        case lae:LockedAccountException => {
          
          log.error("SimpleApacheShiroAdapter: The account for username " + token.getPrincipal() + " is locked.  " +
                    "Please contact your administrator to unlock it.");
          return false
        }
        // ... catch more exceptions here, maybe custom ones specific to your application?
        case ae: AuthenticationException => {
          
          log.error("SimpleApacheShiroAdapter: Unexpected authorization exception " + ae.getMessage())
          return false
        }
        case e: Exception => {
          
          log.error("SimpleApacheShiroAdapter: Unexpected  exception " + e.getMessage())
          return false
        }
      }
    } 

    // Authentication is done, if we have passed it, we need to see if Authorization for this function is valid
    log.debug("SimpleApacheShiroAdapter: User [" + currentUser.getPrincipal() + "] authenticated successfully... checking authorization");

    //test a role:
    if( role != null ){
      if (currentUser.hasRole(role)) {
        log.debug("SimpleApacheShiroAdapter: The role " + role + " is authorized !")
      } else {
        log.debug("SimpleApacheShiroAdapter: The role " + role + " is not authorized !")
        currentUser.logout
        return false
      }
    }
      
    //test a typed permission (not instance-level)
    if( privilege != null ) {
      if (currentUser.isPermitted(privilege)) {
        log.debug("SimpleApacheShiroAdapter: The privilege " + privilege + " is authorized ")
	    } else {
        log.debug("SimpleApacheShiroAdapter: The privilege " + privilege + " is not authorized ")
        currentUser.logout
        return false
      }
    }
    // if we are here.. Both Auth and Authorization is passed..
    log.debug("SimpleApacheShiroAdapter: User [" + currentUser.getPrincipal() + "] authorized successfully");
    currentUser.logout
    return true
  }
}
