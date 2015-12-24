package com.ligadata.TestOutputAdapters

/*
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
 * 
 *  
 */

import org.scalamock.scalatest.MockFactory
import org.scalatest.FlatSpec
import javax.jms.MessageProducer
import javax.jms.Destination
import javax.jms.Connection
import javax.jms.Session

class TestIbmMqProducer extends FlatSpec with MockFactory {
  //printfailure - excluded as it only prints exception 
  //processJmsException - excluded as it only processes JMS exceptions
  //send - uses IBM MQ objects
  //Shutdown - only closes connection, session and producer.


  
}