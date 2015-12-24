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

package com.ligadata.Security

import com.ligadata.SecurityAdapterInfo.SecurityAdapter
import com.sun.security.auth.callback.TextCallbackHandler
import javax.security.auth.login._
import javax.security.auth.Subject;
import java.security._
import javax.security.auth.callback._
import javax.security.auth.kerberos._
import org.apache.logging.log4j._
import com.ligadata.Exceptions.StackTrace

class SampleKerberosActions(inPriv: String) extends java.security.PrivilegedAction[String] {
  private def priv = inPriv
  val loggerName = this.getClass.getName
  val log = LogManager.getLogger(loggerName)
  
  def run: String = {
    // if write is requested, see if we are allowed to access KAMANJA_OBJECT_WRITE System property
    // A security exception will be thrown
    if (priv.equalsIgnoreCase("write")) {
      log.info("user authorized to WRITE")        
    }
    
    if (priv.equalsIgnoreCase("read")) {
      log.info("user authorized to READ")
    }
    
    return null
  } 
  
}

class SimpleKerberosAdapter extends SecurityAdapter {
  var username: String = _
  var password: String = _
  var priv: String = _
  
  /**
   * 
   */
  override def performAuth(secParams: java.util.Properties): Boolean = {
    
    val loggerName = this.getClass.getName
    val log = LogManager.getLogger(loggerName)  
    var mysubject: Subject = new Subject
    var lc: LoginContext = null
    
    username = secParams.getProperty("userid")
    password = secParams.getProperty("password")
    priv = secParams.getProperty("privilige")
 
    // Create a login context based on what "LOGIN" name is configured as.  For this plugin
    // it better be a KERBEROS login manager
    try {
       lc = new LoginContext("Login", mysubject, new MyCallbackHandler);
       log.info ("kerberor LoginManager found")
    } catch {
        case uae: Exception => {
          val stackTrace = StackTrace.ThrowableTraceString(uae)
          log.debug ("\nStackTrace:"+stackTrace)
          return false
        }
    }
    
    // Do Login.  If we dont get an exception here, the subject is authenticated.
    try {
       lc.login();
       log.info ("User "+username+" authenticated")
    } catch{
      case le: LoginException => {
        val stackTrace = StackTrace.ThrowableTraceString(le)
        log.debug ("\nStackTrace:"+stackTrace)
        return false
      }
    } 
    
    // Now see if this subject has the priv.
    try {
      mysubject = lc.getSubject();
      var action: java.security.PrivilegedAction[String] = new SampleKerberosActions(priv);
      Subject.doAsPrivileged(mysubject, action, null);
    } catch {
      case se: SecurityException => {
        val stackTrace = StackTrace.ThrowableTraceString(se)
        log.debug("\nStackTrace:"+stackTrace)
        return false
      }
    } 
    log.info("User "+username+" authorized")
    return true
  }
  
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
   * init - Initialize some information needed for thie plugin.  
   *        1. KAMANJA_OBJECT_READ is created in SYSTEM
   *        2. KAMANJA_OBJECT_WRITE is created in SYSTEM
   */
  override def init:Unit = {
    System.setProperty("KAMANJA_OBJECT_READ", "REQUIRED")
    System.setProperty("KAMANJA_OBJECT_WRITE", "REQUIRED")
  }
  
  // this is a callback that will be used by Kerberos server to collect a username and password
  class MyCallbackHandler extends CallbackHandler {
   def handle(callbacks: Array[Callback]): Unit = {
      callbacks.foreach(callback => {
        callback match {
          case nc:NameCallback => {
            nc.setName(username)
          }
          case pc:PasswordCallback => {
            pc.setPassword(password.toCharArray())
          }
        }
      })   
    }
  } 

}
