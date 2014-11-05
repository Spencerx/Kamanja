package com.ligadata.metadataapiserviceclient

import uk.co.bigbeeconsultants.http.HttpClient
import uk.co.bigbeeconsultants.http.response.Response
import java.net.URL
import org.apache.log4j._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

case class APIResultInfo(statusCode:Int, statusDescription: String, resultData: String)
case class APIResultJsonProxy(APIResults: APIResultInfo)

case class ApiResultParsingException(e: String) extends Throwable(e)
case class Json4sParsingException(e: String) extends Throwable(e)

object GetType {
  val loggerName = this.getClass.getName
  lazy val logger = Logger.getLogger(loggerName)

  @throws(classOf[Json4sParsingException])
  @throws(classOf[ApiResultParsingException])
  def getApiResult(apiResultJson: String): (Int,String) = {
    // parse using Json4s
    try{
      implicit val jsonFormats: Formats = DefaultFormats
      val json = parse(apiResultJson)
      //logger.trace("Parsed the json : " + apiResultJson)
      val apiResultInfo = json.extract[APIResultJsonProxy]
      (apiResultInfo.APIResults.statusCode,apiResultInfo.APIResults.resultData)
    } catch {
      case e:MappingException =>{
	e.printStackTrace()
	throw Json4sParsingException(e.getMessage())
      }
      case e:Exception => {
	e.printStackTrace()
	throw new ApiResultParsingException(e.getMessage())
      }
    }
  }


  def main(args: Array[String]) {
    logger.setLevel(Level.TRACE);
    val httpClient = new HttpClient
    val response: Response = httpClient.get(new URL("http://127.0.0.1:8080/api/GetModel/arrayoflong/Long"))
    logger.trace("response.status => " + response.status)
    val (statusCode,resultData) = getApiResult(response.body.asString)
    logger.trace(resultData)
  }
}
