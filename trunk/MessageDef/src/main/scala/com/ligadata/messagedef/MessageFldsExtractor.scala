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

package com.ligadata.messagedef

import com.ligadata.kamanja.metadata.MdMgr
import com.ligadata.kamanja.metadata.MessageDef
import com.ligadata.kamanja.metadata.BaseAttributeDef
import com.ligadata.kamanja.metadata.ContainerDef
import com.ligadata.kamanja.metadata.ArrayTypeDef
import com.ligadata.kamanja.metadata.ArrayBufTypeDef
import scala.collection.mutable.ArrayBuffer
import com.ligadata.kamanja.metadata.StructTypeDef
import com.ligadata.kamanja.metadata.AttributeDef
import com.ligadata.kamanja.metadata.MappedMsgTypeDef
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.Exceptions.StackTrace

class MessageFldsExtractor {

  var methodGen = new ConstantMethodGenerator
  var msgTypHandler = new MessageTypeHandler
  var cntTypHandler = new ContainerTypeHandler
  var ccptTypHandler = new ConceptTypeHandler
  var arrayTypeHandler = new ArrayTypeHandler
  var baseTypesHandler = new BaseTypesHandler
  var cnstObjVar = new ConstantMsgObjVarGenerator

  val logger = this.getClass.getName
  lazy val log = LogManager.getLogger(logger)

  //generates the variables string and assign string
  def classStr(message: Message, mdMgr: MdMgr, recompile: Boolean): (Array[String], Int, List[(String, String)], List[(String, String, String, String, Boolean, String)], Array[Int], Array[Int]) = {
    var scalaclass = new StringBuilder(8 * 1024)
    var assignCsvdata = new StringBuilder(8 * 1024)
    var assignKvData = new StringBuilder(8 * 1024)
    var assignJsondata = new StringBuilder(8 * 1024)
    var assignXmldata = new StringBuilder(8 * 1024)
    var addMsg = new StringBuilder(8 * 1024)
    var getMsg = new StringBuilder(8 * 1024)
    var getmessage: String = ""
    var keysStr = new StringBuilder(8 * 1024)
    var msgAndCntnrsStr = new StringBuilder(8 * 1024)
    var typeImpl = new StringBuilder(8 * 1024)
    var keysVarStr: String = ""
    var MsgsAndCntrsVarStr: String = ""
    var typeImplStr: String = ""
    var serializedBuf = new StringBuilder(8 * 1024)
    var deserializedBuf = new StringBuilder(8 * 1024)
    var mappedSerBaseTypesBuf = new StringBuilder(8 * 1024)
    var mappedDeserBaseTypesBuf = new StringBuilder(8 * 1024)
    var mappedBaseTypesSet: Set[Int] = Set()

    var prevDeserializedBuf = new StringBuilder(8 * 1024)
    var convertOldObjtoNewObjBuf = new StringBuilder(8 * 1024)
    var collections = new StringBuilder(8 * 1024)
    var mappedPrevVerMatchkeys = new StringBuilder(8 * 1024)
    var mappedMsgFieldsVar = new StringBuilder(8 * 1024)
    var mappedMsgConstructor: String = ""
    var mappedMsgFieldsArry = new StringBuilder(8 * 1024)
    var mappedMsgFieldsArryBuffer = new StringBuilder(8 * 1024)
    var mappedPrevTypNotMatchkeys = new StringBuilder(8 * 1024)
    var prevObjTypNotMatchDeserializedBuf = new StringBuilder(8 * 1024)
    var fixedMsgGetKeyStrBuf = new StringBuilder(8 * 1024)
    var withMethods = new StringBuilder(8 * 1024)
    var fromFuncBuf = new StringBuilder(8 * 1024)
    var fromFuncBaseTypesBuf = new StringBuilder(8 * 1024)
    var timePartitionPos: String = ""
    var timePartitionFld: String = ""
    var nativeKeyMapBuf = new StringBuilder(8 * 1024)
    var getNativeKeyValues = new StringBuilder(8 * 1024)


    var list = List[(String, String)]()
    var argsList = List[(String, String, String, String, Boolean, String)]()
    val pad1 = "\t"
    val pad2 = "\t\t"
    val pad3 = "\t\t\t"
    val pad4 = "\t\t\t\t"
    val newline = "\n"
    var jarset: Set[String] = Set();
    var arrayType: ArrayTypeDef = null
    var arrayBufType: ArrayBufTypeDef = null
    var fname: String = ""
    var count: Int = 0
    var concepts: String = "concepts"
    var keysSet: Set[String] = Set()
    var keys: Set[String] = Set()

    var ftypeVersion: Long = -1
    var addMessage: String = ""

    var partitionPos = Array[Int]()
    var primaryPos = Array[Int]()
    var prevVerMsgObjstr: String = ""
    var childs: Map[String, Any] = Map[String, Any]()
    var prevVerMsgBaseTypesIdxArry = new ArrayBuffer[String]
    try {

      //Mapped Messages - Add String as first element to Array Buffer for typs variable
      var mappedTypesABuf = new scala.collection.mutable.ArrayBuffer[String]
      val stringType = MdMgr.GetMdMgr.Type("System.String", -1, true)
      if (stringType.getOrElse("None").equals("None"))
        throw new Exception("Type not found in metadata for String ")
      mappedTypesABuf += stringType.get.implementationName
      var firstTimeBaseType: Boolean = true
      //Get the previous added Message or Container from Metadata and get all the childs from the Previous message/Container for Deserialize purpose

      val (ismsg, pMsgdef, pMsgObjstr) = getPrevVersionMsgContainer(message: Message, mdMgr: MdMgr)
      prevVerMsgObjstr = pMsgObjstr
      val isMsg = ismsg
      if (pMsgdef != null) {
        val (childAttrs, prevVerMsgBaseTypesIdxArray) = getPrevVerMsgChilds(pMsgdef, isMsg, message.Fixed)
        childs = childAttrs

        if ((pMsgdef.dependencyJarNames != null) && (pMsgdef.JarName != null))
          jarset = jarset + pMsgdef.JarName ++ pMsgdef.dependencyJarNames
        else if (pMsgdef.JarName != null)
          jarset = jarset + pMsgdef.JarName
        else if (pMsgdef.dependencyJarNames != null)
          jarset = jarset ++ pMsgdef.dependencyJarNames
      }

      scalaclass = scalaclass.append(cnstObjVar.getIsFixedCls(message) + newline + cnstObjVar.getCanPersistCls(message) + newline + cnstObjVar.getNameVerCls(message) + newline + newline)
      // for (e <- message.Elements) {
      //  var fields = e.Fields
      var paritionkeys: List[String] = null
      var primaryKeys: List[String] = null
      // if ((message.PartitionKey != null) && (message.PartitionKey(0) != null) && (message.PartitionKey(0).trim() != ""))
      //    paritionkey = message.PartitionKey(0).toLowerCase()
      if (message.PartitionKey != null)
        paritionkeys = message.PartitionKey

      if (message.PrimaryKeys != null)
        primaryKeys = message.PrimaryKeys

      if (paritionkeys != null && paritionkeys.size > 0)
        paritionkeys.foreach { partitionkey =>
          keys = keys + partitionkey.toLowerCase()
        }

      if (primaryKeys != null && primaryKeys.size > 0)
        primaryKeys.foreach { primarykey =>
          keys = keys + primarykey.toLowerCase()
        }

      if (message.Elements != null)
        for (f <- message.Elements) {
          if (!f.Name.equalsIgnoreCase("timePartitionData")) {
            // val typ = MdMgr.GetMdMgr.Type(key, ver, onlyActive)(f.Ttype)
            //val attr = MdMgr.GetMdMgr.Attribute(message.NameSpace, message.Name)
            //  val container = MdMgr.GetMdMgr.Containers(onlyActive, latestVersion)
            if (keysSet.contains(f.Name)) throw new Exception("Duplicate Key " + f.Name + " Exists, please check Message Definition JSON")
            keysSet = keysSet + f.Name

            if (f.FieldtypeVer != null)
              ftypeVersion = MdMgr.ConvertVersionToLong(message.Version)

            if ((f.ElemType.equals("Field")) || (f.ElemType.equals("Fields"))) {

              log.debug("message.Version " + MdMgr.ConvertVersionToLong(message.Version))

              val typ = MdMgr.GetMdMgr.Type(f.Ttype, ftypeVersion, true) // message.Version.toLong

              if (typ.getOrElse("None").equals("None"))
                throw new Exception("Type not found in metadata for Name: " + f.Name + " , NameSpace: " + f.NameSpace + " , Version: " + message.Version + " , Type : " + f.Ttype)

              if (typ.get.tType == null) throw new Exception("tType in Type do not exists")

              if (typ.get.tType.toString().equals("tArray")) {

                val (arr_1, arr_2, arr_3, arr_4) = arrayTypeHandler.handleArrayType(keys, typ, f, message, childs, prevVerMsgBaseTypesIdxArry, recompile)

                list = arr_1
                argsList = argsList ::: arr_2
                jarset = jarset ++ arr_3

                scalaclass = scalaclass.append(arr_4(0))
                assignCsvdata = assignCsvdata.append(arr_4(1))
                assignJsondata = assignJsondata.append(arr_4(2))
                assignXmldata = assignXmldata.append(arr_4(3))
                addMsg = addMsg.append(arr_4(4))
                msgAndCntnrsStr = msgAndCntnrsStr.append(arr_4(5))
                getMsg = getMsg.append(arr_4(6))
                serializedBuf = serializedBuf.append(arr_4(7))
                deserializedBuf = deserializedBuf.append(arr_4(8))
                prevDeserializedBuf = prevDeserializedBuf.append(arr_4(9))
                convertOldObjtoNewObjBuf = convertOldObjtoNewObjBuf.append(arr_4(10))
                collections = collections.append(arr_4(11))
                mappedPrevVerMatchkeys = mappedPrevVerMatchkeys.append(arr_4(12))
                mappedMsgFieldsArry = mappedMsgFieldsArry.append(arr_4(13))
                mappedPrevTypNotMatchkeys = mappedPrevTypNotMatchkeys.append(arr_4(14))
                fixedMsgGetKeyStrBuf = fixedMsgGetKeyStrBuf.append(arr_4(15))
                withMethods = withMethods.append(arr_4(16))
                fromFuncBuf = fromFuncBuf.append(arr_4(17))
                assignKvData = assignKvData.append(arr_4(18))
                nativeKeyMapBuf = nativeKeyMapBuf.append(arr_4(19))
                getNativeKeyValues = getNativeKeyValues.append(arr_4(20))


                //       =  assignCsvdata.toString, assignJsondata.toString, assignXmldata.toString,  list, argsList, addMsg.toString)  = 
              } else if (typ.get.tType.toString().equals("tArrayBuf")) {
                val (arrayBuf_1, arrayBuf_2, arrayBuf_3, arrayBuf_4) = arrayTypeHandler.handleArrayBuffer(keys, message, typ, f, childs, prevVerMsgBaseTypesIdxArry, recompile)
                list = arrayBuf_1
                argsList = argsList ::: arrayBuf_2
                jarset = jarset ++ arrayBuf_3

                scalaclass = scalaclass.append(arrayBuf_4(0))
                assignCsvdata = assignCsvdata.append(arrayBuf_4(1))
                assignJsondata = assignJsondata.append(arrayBuf_4(2))
                assignXmldata = assignXmldata.append(arrayBuf_4(3))
                addMsg = addMsg.append(arrayBuf_4(4))
                msgAndCntnrsStr = msgAndCntnrsStr.append(arrayBuf_4(5))
                keysStr = keysStr.append(arrayBuf_4(6))
                getMsg = getMsg.append(arrayBuf_4(7))
                serializedBuf = serializedBuf.append(arrayBuf_4(8))
                deserializedBuf = deserializedBuf.append(arrayBuf_4(9))
                prevDeserializedBuf = prevDeserializedBuf.append(arrayBuf_4(10))
                convertOldObjtoNewObjBuf = convertOldObjtoNewObjBuf.append(arrayBuf_4(11))
                collections = collections.append(arrayBuf_4(12))
                mappedPrevVerMatchkeys = mappedPrevVerMatchkeys.append(arrayBuf_4(13))
                mappedMsgFieldsVar = mappedMsgFieldsVar.append(arrayBuf_4(14))
                mappedPrevTypNotMatchkeys = mappedPrevTypNotMatchkeys.append(arrayBuf_4(15))
                mappedMsgFieldsArryBuffer = mappedMsgFieldsArryBuffer.append(arrayBuf_4(16))
                fixedMsgGetKeyStrBuf = fixedMsgGetKeyStrBuf.append(arrayBuf_4(17))
                withMethods = withMethods.append(arrayBuf_4(18))
                fromFuncBuf = fromFuncBuf.append(arrayBuf_4(19))
                assignKvData = assignKvData.append(arrayBuf_4(20))
                nativeKeyMapBuf = nativeKeyMapBuf.append(arrayBuf_4(21))
                getNativeKeyValues = getNativeKeyValues.append(arrayBuf_4(22))


              } else if (typ.get.tType.toString().equals("tHashMap")) {

                assignCsvdata.append(newline + "//HashMap not handled at this momemt" + newline)
                assignJsondata.append(newline + "//HashMap not handled at this momemt" + newline)
              } else if (typ.get.tType.toString().equals("tTreeSet")) {

                assignCsvdata.append(newline + "//Tree Set not handled at this momemt" + newline)
                assignJsondata.append(newline + "//Tree Set not handled at this momemt" + newline)
              } else {
                val (baseTyp_1, baseTyp_2, baseTyp_3, baseTyp_4, baseTyp_5, baseTyp_6) = baseTypesHandler.handleBaseTypes(keys, message.Fixed, typ, f, message.Version, childs, prevVerMsgBaseTypesIdxArry, recompile, mappedTypesABuf, firstTimeBaseType, message)
                list = baseTyp_1
                argsList = argsList ::: baseTyp_2
                jarset = jarset ++ baseTyp_3
                mappedTypesABuf = baseTyp_4
                prevVerMsgBaseTypesIdxArry = baseTyp_5

                scalaclass = scalaclass.append(baseTyp_6(0))
                assignCsvdata = assignCsvdata.append(baseTyp_6(1))
                assignJsondata = assignJsondata.append(baseTyp_6(2))
                assignXmldata = assignXmldata.append(baseTyp_6(3))

                addMsg = addMsg.append(baseTyp_6(4))
                keysStr = keysStr.append(baseTyp_6(5))
                typeImpl = typeImpl.append(baseTyp_6(6))

                if (message.Fixed.toLowerCase().equals("true")) {
                  serializedBuf = serializedBuf.append(baseTyp_6(7))
                  deserializedBuf = deserializedBuf.append(baseTyp_6(8))
                } else if (message.Fixed.toLowerCase().equals("false")) {
                  mappedSerBaseTypesBuf = mappedSerBaseTypesBuf.append(baseTyp_6(7))
                  mappedDeserBaseTypesBuf = mappedDeserBaseTypesBuf.append(baseTyp_6(8))
                }

                prevDeserializedBuf = prevDeserializedBuf.append(baseTyp_6(9))
                convertOldObjtoNewObjBuf = convertOldObjtoNewObjBuf.append(baseTyp_6(10))

                mappedPrevVerMatchkeys = mappedPrevVerMatchkeys.append(baseTyp_6(11))
                mappedPrevTypNotMatchkeys = mappedPrevTypNotMatchkeys.append(baseTyp_6(12))
                prevObjTypNotMatchDeserializedBuf = prevObjTypNotMatchDeserializedBuf.append(baseTyp_6(13))

                fixedMsgGetKeyStrBuf = fixedMsgGetKeyStrBuf.append(baseTyp_6(14))
                withMethods = withMethods.append(baseTyp_6(15))
                fromFuncBaseTypesBuf = fromFuncBaseTypesBuf.append(baseTyp_6(16))
                assignKvData = assignKvData.append(baseTyp_6(17))
                nativeKeyMapBuf = nativeKeyMapBuf.append(baseTyp_6(18))
                getNativeKeyValues = getNativeKeyValues.append(baseTyp_6(19))


                if (paritionkeys != null && paritionkeys.size > 0) {
                  if (paritionkeys.contains(f.Name)) {
                    partitionPos = partitionPos :+ count
                  }
                }
                if (primaryKeys != null && primaryKeys.size > 0) {
                  if (primaryKeys.contains(f.Name)) {
                    primaryPos = primaryPos :+ count
                  }
                }
                firstTimeBaseType = false

              }
              if (message.timePartition != null && message.timePartition.Key.equalsIgnoreCase(f.Name)) {
                timePartitionPos = count.toString
                timePartitionFld = message.timePartition.Key

              }
              count = count + 1

            } else if ((f.ElemType.equals("Container")) || (f.ElemType.equals("Message"))) {

              if (f.ElemType.equals("Message"))
                throw new Exception("Adding Child Messages are not allowed in Message/Container Definition")

              //val typ = MdMgr.GetMdMgr.Type(f.Ttype, ftypeVersion, true) // message.Version.toLong
              val typ = MdMgr.GetMdMgr.Type(f.Ttype, -1, true) // message.Version.toLong

              if (typ.getOrElse("None").equals("None"))
                throw new Exception("Type not found in metadata for Name: " + f.Name + " , NameSpace: " + f.NameSpace + " , Version: " + message.Version + " , Type : " + f.Ttype)

              if (typ.getOrElse(null) != null) {
                if (typ.get.tType.toString().equals("tArrayBuf")) {
                  val (arrayBuf_1, arrayBuf_2, arrayBuf_3, arrayBuf_4) = arrayTypeHandler.handleArrayBuffer(keys, message, typ, f, childs, prevVerMsgBaseTypesIdxArry, recompile)
                  list = arrayBuf_1
                  argsList = argsList ::: arrayBuf_2
                  jarset = jarset ++ arrayBuf_3
                  scalaclass = scalaclass.append(arrayBuf_4(0))
                  assignCsvdata = assignCsvdata.append(arrayBuf_4(1))
                  assignJsondata = assignJsondata.append(arrayBuf_4(2))
                  assignXmldata = assignXmldata.append(arrayBuf_4(3))
                  addMsg = addMsg.append(arrayBuf_4(4))
                  msgAndCntnrsStr = msgAndCntnrsStr.append(arrayBuf_4(5))
                  keysStr = keysStr.append(arrayBuf_4(6))
                  getMsg = getMsg.append(arrayBuf_4(7))
                  serializedBuf = serializedBuf.append(arrayBuf_4(8))
                  deserializedBuf = deserializedBuf.append(arrayBuf_4(9))
                  prevDeserializedBuf = prevDeserializedBuf.append(arrayBuf_4(10))
                  convertOldObjtoNewObjBuf = convertOldObjtoNewObjBuf.append(arrayBuf_4(11))
                  collections = collections.append(arrayBuf_4(12))
                  mappedPrevVerMatchkeys = mappedPrevVerMatchkeys.append(arrayBuf_4(13))
                  mappedMsgFieldsVar = mappedMsgFieldsVar.append(arrayBuf_4(14))
                  mappedPrevTypNotMatchkeys = mappedPrevTypNotMatchkeys.append(arrayBuf_4(15))
                  mappedMsgFieldsArryBuffer = mappedMsgFieldsArryBuffer.append(arrayBuf_4(16))
                  fixedMsgGetKeyStrBuf = fixedMsgGetKeyStrBuf.append(arrayBuf_4(17))
                  withMethods = withMethods.append(arrayBuf_4(18))
                  fromFuncBuf = fromFuncBuf.append(arrayBuf_4(19))
                  assignKvData = assignKvData.append("// Not Handling Array of Container")
                  nativeKeyMapBuf = nativeKeyMapBuf.append(arrayBuf_4(21))
                  getNativeKeyValues = getNativeKeyValues.append(arrayBuf_4(22))


                } else if (typ.get.tType.toString().equals("tArray")) {

                  val (arr_1, arr_2, arr_3, arr_4) = arrayTypeHandler.handleArrayType(keys, typ, f, message, childs, prevVerMsgBaseTypesIdxArry, recompile)

                  list = arr_1
                  argsList = argsList ::: arr_2
                  jarset = jarset ++ arr_3
                  scalaclass = scalaclass.append(arr_4(0))
                  assignCsvdata = assignCsvdata.append(arr_4(1))
                  assignJsondata = assignJsondata.append(arr_4(2))
                  assignXmldata = assignXmldata.append(arr_4(3))
                  addMsg = addMsg.append(arr_4(4))
                  msgAndCntnrsStr = msgAndCntnrsStr.append(arr_4(5))
                  getMsg = getMsg.append(arr_4(6))
                  serializedBuf = serializedBuf.append(arr_4(7))
                  deserializedBuf = deserializedBuf.append(arr_4(8))
                  prevDeserializedBuf = prevDeserializedBuf.append(arr_4(9))
                  convertOldObjtoNewObjBuf = convertOldObjtoNewObjBuf.append(arr_4(10))
                  collections = collections.append(arr_4(11))
                  mappedPrevVerMatchkeys = mappedPrevVerMatchkeys.append(arr_4(12))
                  mappedMsgFieldsArry = mappedMsgFieldsArry.append(arr_4(13))
                  mappedPrevTypNotMatchkeys = mappedPrevTypNotMatchkeys.append(arr_4(14))
                  fixedMsgGetKeyStrBuf = fixedMsgGetKeyStrBuf.append(arr_4(15))
                  withMethods = withMethods.append(arr_4(16))
                  fromFuncBuf = fromFuncBuf.append(arr_4(17))
                  assignKvData = assignKvData.append("// Not Handling Array of Container")
                  nativeKeyMapBuf = nativeKeyMapBuf.append(arr_4(19))
                  getNativeKeyValues = getNativeKeyValues.append(arr_4(20))


                  //       =  assignCsvdata.toString, assignJsondata.toString, assignXmldata.toString,  list, argsList, addMsg.toString)  = 
                } else {

                  if (f.ElemType.equals("Container")) {

                    val (cntnr_1, cntnr_2, cntnr_3, cntnr_4) = cntTypHandler.handleContainer(message, mdMgr, ftypeVersion, f, recompile, childs)
                    list = cntnr_1
                    argsList = argsList ::: cntnr_2
                    jarset = jarset ++ cntnr_3
                    scalaclass = scalaclass.append(cntnr_4(0))
                    assignCsvdata = assignCsvdata.append(cntnr_4(1))
                    assignJsondata = assignJsondata.append(cntnr_4(2))
                    assignXmldata = assignXmldata.append(cntnr_4(3))
                    addMsg = addMsg.append(cntnr_4(4))
                    msgAndCntnrsStr = msgAndCntnrsStr.append(cntnr_4(5))
                    serializedBuf = serializedBuf.append(cntnr_4(6))
                    deserializedBuf = deserializedBuf.append(cntnr_4(7))
                    prevDeserializedBuf = prevDeserializedBuf.append(cntnr_4(8))
                    convertOldObjtoNewObjBuf = convertOldObjtoNewObjBuf.append(cntnr_4(9))
                    mappedPrevVerMatchkeys = mappedPrevVerMatchkeys.append(cntnr_4(10))
                    mappedPrevTypNotMatchkeys = mappedPrevTypNotMatchkeys.append(cntnr_4(11))
                    fixedMsgGetKeyStrBuf = fixedMsgGetKeyStrBuf.append(cntnr_4(12))

                    withMethods = withMethods.append(cntnr_4(13))
                    fromFuncBuf = fromFuncBuf.append(cntnr_4(14))
                    nativeKeyMapBuf = nativeKeyMapBuf.append(cntnr_4(15))
                  
                    assignKvData = assignKvData.append("// Not Handling Container")
                    getNativeKeyValues = getNativeKeyValues.append(cntnr_4(16))
                
                    //   mappedPrevVerMatchkeys

                  }
                  /**
                   * Commenting the below code since the child messages are not allowed in message def.
                   *
                   */
                  /*else if (f.ElemType.equals("Message")) {
                  val (msg_1, msg_2, msg_3, msg_4, msg_5, msg_6, msg_7, msg_8, msg_9, msg_10, msg_11, msg_12, msg_13, msg_14, msg_15) = handleMessage(mdMgr, ftypeVersion, f, message, childs, recompile)
                  scalaclass = scalaclass.append(msg_1)
                  assignCsvdata = assignCsvdata.append(msg_2)
                  assignJsondata = assignJsondata.append(msg_3)
                  assignXmldata = assignXmldata.append(msg_4)
                  list = msg_5
                  argsList = argsList ::: msg_6
                  addMsg = addMsg.append(msg_7)
                  jarset = jarset ++ msg_8
                  getMsg = getMsg.append(msg_9)
                  serializedBuf = serializedBuf.append(msg_10)
                  deserializedBuf = deserializedBuf.append(msg_11)
                  prevDeserializedBuf = prevDeserializedBuf.append(msg_12)
                  convertOldObjtoNewObjBuf = convertOldObjtoNewObjBuf.append(msg_13)
                  mappedPrevVerMatchkeys = mappedPrevVerMatchkeys.append(msg_14)
                  mappedPrevTypNotMatchkeys = mappedPrevTypNotMatchkeys.append(msg_15)

                  // mappedPrevVerMatchkeys

                }
                * */

                }
              }
            } else if (f.ElemType.equals("Concept")) {
              val (ccpt_1, ccpt_2, ccpt_3, ccpt_4, ccpt_5, ccpt_6, ccpt_7, ccpt_8, ccpt_9, ccpt_10, ccpt_11) = ccptTypHandler.handleConcept(mdMgr, ftypeVersion, f, message)
              scalaclass = scalaclass.append(ccpt_1)
              assignCsvdata = assignCsvdata.append(ccpt_2)
              assignJsondata = assignJsondata.append(ccpt_3)
              assignXmldata = assignXmldata.append(ccpt_4)
              list = ccpt_5
              argsList = argsList ::: ccpt_6
              addMsg = addMsg.append(ccpt_7)
              jarset = jarset ++ ccpt_8
              fixedMsgGetKeyStrBuf = fixedMsgGetKeyStrBuf.append(ccpt_9)
              withMethods = withMethods.append(ccpt_10)
              fromFuncBuf = fromFuncBuf.append(ccpt_11)
            }
          }
        }
      if (message.concepts != null) {

      }

      var partitionKeys: String = ""
      var prmryKeys: String = ""

      if (message.Fixed.toLowerCase().equals("false")) {

        //if (keysStr != null && keysStr.toString.trim != "")
        keysVarStr = cnstObjVar.getKeysStr(keysStr.toString)
        MsgsAndCntrsVarStr = cnstObjVar.getMsgAndCntnrs(msgAndCntnrsStr.toString)
        //typeImplStr = getTypeImplStr(typeImpl.toString)
        // mappedMsgConstructor = getAddMappedMsgsInConstructor(mappedMsgFieldsVar.toString)

        scalaclass.append(keysVarStr + newline + pad1 + typeImplStr + newline + pad1 + MsgsAndCntrsVarStr + newline + pad1 + cnstObjVar.getMappedBaseTypesArray(mappedTypesABuf) + newline)
        scalaclass.append(methodGen.getAddMappedMsgsInConstructor(mappedMsgFieldsVar.toString) + methodGen.getAddMappedArraysInConstructor(mappedMsgFieldsArry.toString, mappedMsgFieldsArryBuffer.toString) + cnstObjVar.getMappedMsgPrevVerMatchKeys(mappedPrevVerMatchkeys.toString) + methodGen.mappedToStringForKeys)
        scalaclass.append(cnstObjVar.getMappedMsgPrevVerTypeNotMatchKeys(mappedPrevTypNotMatchkeys.toString) + methodGen.mappedPrevObjTypNotMatchDeserializedBuf(prevObjTypNotMatchDeserializedBuf.toString))

        partitionKeys = if (message.PartitionKey != null && message.PartitionKey.size > 0) ("Array(" + message.PartitionKey.map(p => "toStringForKey(\"" + p.toLowerCase + "\")").mkString(", ") + ")") else ""
        prmryKeys = if (message.PrimaryKeys != null && message.PrimaryKeys.size > 0) ("Array(" + message.PrimaryKeys.map(p => "toStringForKey(\"" + p.toLowerCase + "\")").mkString(", ") + ")") else ""

      } else if (message.Fixed.toLowerCase().equals("true")) {
        partitionKeys = if (message.PartitionKey != null && message.PartitionKey.size > 0) ("Array(" + message.PartitionKey.map(p => p.toLowerCase + ".toString").mkString(", ") + ")") else ""
        prmryKeys = if (message.PrimaryKeys != null && message.PrimaryKeys.size > 0) ("Array(" + message.PrimaryKeys.map(p => p.toLowerCase + ".toString").mkString(", ") + ")") else ""

      }

      /*
      var partitionKeyStr = new StringBuilder(8 * 1024)
      if (message.PartitionKey != null)
        message.PartitionKey.foreach { p =>
          partitionKeyStr = partitionKeyStr.append("\"" + p.toLowerCase() + "\",")
        }
      var partitionKeys: String = ""
      if (partitionKeyStr != null && partitionKeyStr.toString.trim() != "")
        partitionKeys = "scala.Array(" + partitionKeyStr.substring(0, partitionKeyStr.toString.length() - 1) + ")"
*/

      scalaclass = scalaclass.append(cnstObjVar.partitionkeyStr(partitionKeys) + newline + cnstObjVar.primarykeyStr(prmryKeys) + newline + cnstObjVar.getsetMethods(message, fixedMsgGetKeyStrBuf.toString))

      if (getMsg != null && getMsg.toString.trim() != "" && getMsg.size > 5)
        getmessage = getMsg.toString.substring(0, getMsg.length - 5)
      if (addMsg != null && addMsg.toString.trim() != "" && addMsg.size > 5)
        addMessage = addMsg.toString.substring(0, addMsg.length - 5)

      log.debug("final arglist " + argsList)
      if (jarset != null)
        message.jarset = jarset

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }

    var returnClassStr = new ArrayBuffer[String]

    returnClassStr += scalaclass.toString
    returnClassStr += assignCsvdata.toString
    returnClassStr += assignJsondata.toString
    returnClassStr += assignXmldata.toString
    returnClassStr += addMessage
    returnClassStr += getmessage
    returnClassStr += serializedBuf.toString
    returnClassStr += deserializedBuf.toString
    returnClassStr += prevDeserializedBuf.toString
    returnClassStr += prevVerMsgObjstr
    returnClassStr += convertOldObjtoNewObjBuf.toString
    returnClassStr += collections.toString
    returnClassStr += mappedSerBaseTypesBuf.toString
    returnClassStr += mappedDeserBaseTypesBuf.toString
    returnClassStr += withMethods.toString
    returnClassStr += fromFuncBuf.toString
    returnClassStr += fromFuncBaseTypesBuf.toString
    returnClassStr += assignKvData.toString
    returnClassStr += timePartitionFld.toString
    returnClassStr += timePartitionPos.toString
    returnClassStr += nativeKeyMapBuf.toString
    returnClassStr += getNativeKeyValues.toString

    (returnClassStr.toArray, count, list, argsList, partitionPos, primaryPos)

  }

  //Get the actve Message or container from Metadata 
  def getRecompiledMsgContainerVersion(messagetype: String, namespace: String, name: String, mdMgr: MdMgr): String = {
    var msgdef: Option[ContainerDef] = null

    if (namespace == null || namespace.trim() == "")
      throw new Exception("Proper Namespace do not exists in message/container definition")
    if (name == null || name.trim() == "")
      throw new Exception("Proper Name do not exists in message")
    if (messagetype == null || messagetype.trim() == "")
      throw new Exception("Proper Version do not exists in message/container definition")

    try {

      if (messagetype.toLowerCase().equals("message")) {
        msgdef = mdMgr.Message(namespace, name, -1, false)
        val isMsg = true
      } else if (messagetype.toLowerCase().equals("container")) {
        msgdef = mdMgr.Container(namespace, name, -1, false)
      }

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
      }
    }
    log.debug("version from metadata " + msgdef.get.Version)

    var newver = msgdef.get.Version + 1

    MdMgr.ConvertLongVersionToString(newver)
  }

  private def getPrevVerMsgChilds(pMsgdef: ContainerDef, isMsg: Boolean, fixed: String): (Map[String, Any], ArrayBuffer[String]) = {
    var prevVerCtrdef: ContainerDef = new ContainerDef()
    var prevVerMsgdef: MessageDef = new MessageDef()
    var childs: Map[String, Any] = Map[String, Any]()
    var prevVerMsgBaseTypesIdxArry = new ArrayBuffer[String]

    if (pMsgdef != null) {

      if (isMsg) {
        prevVerCtrdef = pMsgdef.asInstanceOf[MessageDef]
      } else {
        prevVerCtrdef = pMsgdef.asInstanceOf[ContainerDef]
      }
      if (fixed.toLowerCase().equals("true")) {
        val memberDefs = prevVerCtrdef.containerType.asInstanceOf[StructTypeDef].memberDefs
        if (memberDefs != null) {
          childs ++= memberDefs.filter(a => (a.isInstanceOf[AttributeDef])).map(a => (a.Name, a))
        }
      } else if (fixed.toLowerCase().equals("false")) {
        val attrMap = prevVerCtrdef.containerType.asInstanceOf[MappedMsgTypeDef].attrMap
        if (attrMap != null) {

          attrMap.foreach(a => {
            val attributes = a._2.asInstanceOf[AttributeDef].aType
            if (attributes != null)
              if (attributes.implementationName != null && attributes.tTypeType.toString().toLowerCase().equals("tscalar") && !prevVerMsgBaseTypesIdxArry.contains(a._2.asInstanceOf[AttributeDef].aType.implementationName))
                prevVerMsgBaseTypesIdxArry += a._2.asInstanceOf[AttributeDef].aType.implementationName;
          })
          childs ++= attrMap.filter(a => (a._2.isInstanceOf[AttributeDef])).map(a => (a._2.Name, a._2))
        }
      }
    }
    (childs, prevVerMsgBaseTypesIdxArry)
  }

  private def getPrevVersionMsgContainer(message: Message, mdMgr: MdMgr): (Boolean, ContainerDef, String) = {
    var msgdef: ContainerDef = null
    var msgdefobj: Option[ContainerDef] = null
    var prevVerMsgObjstr: String = ""
    var childs: ArrayBuffer[(String, String)] = ArrayBuffer[(String, String)]()
    var isMsg: Boolean = false
    try {
      val messagetype = message.msgtype
      val namespace = message.NameSpace
      val name = message.Name
      val ver = message.Version

      if (namespace == null || namespace.trim() == "")
        throw new Exception("Proper Namespace do not exists in message/container definition")
      if (name == null || name.trim() == "")
        throw new Exception("Proper Name do not exists in message")
      if (ver == null || ver.trim() == "")
        throw new Exception("Proper Version do not exists in message/container definition")

      if (messagetype != null && messagetype.trim() != "") {

        if (messagetype.toLowerCase().equals("message")) {
          msgdefobj = mdMgr.Message(namespace, name, -1, false)
          val isMsg = true
        } else if (messagetype.toLowerCase().equals("container")) {
          msgdefobj = mdMgr.Container(namespace, name, -1, false)
        }

        msgdefobj match {
          case None =>
            msgdef = null
          case Some(m) =>
            if (isMsg)
              msgdef = m.asInstanceOf[MessageDef]
            else
              msgdef = m.asInstanceOf[ContainerDef]
            val fullname = msgdef.FullNameWithVer.replaceAll("[.]", "_")
            prevVerMsgObjstr = msgdef.PhysicalName
        }
      }

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
      }
    }

    (isMsg, msgdef, prevVerMsgObjstr)

  }

}