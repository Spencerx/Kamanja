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

package com.ligadata.kamanja.samples.models

import com.ligadata.KamanjaBase._
import RddUtils._
import RddDate._
import com.ligadata.KamanjaBase.MinVarType._
import java.text.SimpleDateFormat
import java.util.Calendar
import scala.io.Source
import scala.collection.JavaConversions._
import java.util._
import org.joda.time._
import com.ligadata.kamanja.metadata.ModelDef;

class COPDRiskAssessmentFactory(modelDef: ModelDef, nodeContext: NodeContext) extends ModelInstanceFactory(modelDef, nodeContext) {
  override def isValidMessage(msg: MessageContainerBase): Boolean = return msg.isInstanceOf[Beneficiary]
  override def createModelInstance(): ModelInstance = return new COPDRiskAssessment(this)
  override def getModelName: String = "COPDRisk"
  override def getVersion: String = "0.0.1"
  override def createResultObject(): ModelResultBase = new MappedModelResults()
}

class COPDRiskAssessment(factory: ModelInstanceFactory) extends ModelInstance(factory) {
  override def execute(txnCtxt: TransactionContext, outputDefault: Boolean): ModelResultBase = {
    var msgBeneficiary: Beneficiary = txnCtxt.getMessage().asInstanceOf[Beneficiary]
    val smokingCodeSet: Array[String] = SmokeCodes.getRDD.map { x => (x.icd9code) }.toArray
    val sputumCodeSet: Array[String] = SputumCodes.getRDD.map { x => (x.icd9code) }.toArray
    val envExposureCodeSet: Array[String] = EnvCodes.getRDD.map { x => (x.icd9code) }.toArray
    val coughCodeSet: Array[String] = CoughCodes.getRDD.map { x => (x.icd9code) }.toArray
    val dyspnoeaCodeSet: Array[String] = DyspnoeaCodes.getRDD.map { x => (x.icd9code) }.toArray
    var age: Int = 0
    val cal: Calendar = Calendar.getInstance
    cal.add(Calendar.YEAR, -1)
    var today: Date = Calendar.getInstance.getTime
    var oneYearBeforeDate = cal.getTime
    var originalFormat: SimpleDateFormat = new SimpleDateFormat("yyyyMMdd")
    val inPatientInfoThisLastyear: RDD[InpatientClaim] = InpatientClaim.getRDD(msgBeneficiary.PartitionKeyData).filter { x =>
      originalFormat.parse(x.clm_thru_dt.toString()).before(today) && originalFormat.parse(x.clm_thru_dt.toString()).after(oneYearBeforeDate)
    }

    val outPatientInfoThisLastYear: RDD[OutpatientClaim] = OutpatientClaim.getRDD(msgBeneficiary.PartitionKeyData).filter { x =>
      originalFormat.parse(x.clm_thru_dt.toString()).before(today) && originalFormat.parse(x.clm_thru_dt.toString()).after(oneYearBeforeDate)
    }

    def getOverSmokingCodesInLastYear(): Boolean = {
      for (x <- inPatientInfoThisLastyear) {
        if (smokingCodeSet.contains(x.admtng_icd9_dgns_cd)) {
          return true
        }

        for (s <- x.icd9_dgns_cds) {
          if (smokingCodeSet.contains(s)) {
            return true
          }
        }
      }
      for (x <- outPatientInfoThisLastYear) {
        if (smokingCodeSet.contains(x.admtng_icd9_dgns_cd)) {
          return true
        }

        for (s <- x.icd9_dgns_cds) {
          if (smokingCodeSet.contains(s)) {
            return true
          }
        }
      }

      return false
    }

    def getEnvironmentalExposuresInLastYear(): Boolean = {
      for (x <- inPatientInfoThisLastyear) {
        if (envExposureCodeSet.contains(x.admtng_icd9_dgns_cd)) {
          return true
        }

        for (s <- x.icd9_dgns_cds) {
          if (envExposureCodeSet.contains(s)) {
            return true
          }
        }
      }
      for (x <- outPatientInfoThisLastYear) {
        if (envExposureCodeSet.contains(x.admtng_icd9_dgns_cd)) {
          return true
        }

        for (s <- x.icd9_dgns_cds) {
          if (envExposureCodeSet.contains(s)) {
            return true
          }
        }
      }

      return false
    }

    def getDyspnoeaInLastYear(): Boolean = {
      for (x <- inPatientInfoThisLastyear) {
        if (dyspnoeaCodeSet.contains(x.admtng_icd9_dgns_cd)) {
          return true
        }

        for (s <- x.icd9_dgns_cds) {
          if (dyspnoeaCodeSet.contains(s)) {
            return true
          }
        }
      }
      for (x <- outPatientInfoThisLastYear) {

        if (dyspnoeaCodeSet.contains(x.admtng_icd9_dgns_cd)) {
          return true
        }

        for (s <- x.icd9_dgns_cds) {
          if (dyspnoeaCodeSet.contains(s)) {
            return true
          }
        }

      }

      return false
    }

    def getChronicCoughInLastYear(): Boolean = {
      for (x <- inPatientInfoThisLastyear) {
        if (coughCodeSet.contains(x.admtng_icd9_dgns_cd)) {
          return true
        }

        for (s <- x.icd9_dgns_cds) {
          if (coughCodeSet.contains(s)) {
            return true
          }
        }

      }

      for (x <- outPatientInfoThisLastYear) {

        if (coughCodeSet.contains(x.admtng_icd9_dgns_cd)) {
          return true
        }

        for (s <- x.icd9_dgns_cds) {
          if (coughCodeSet.contains(s)) {
            return true
          }
        }
      }

      return false
    }

    def getChronicSputumInLastYear(): Boolean = {
      for (x <- inPatientInfoThisLastyear) {

        if (sputumCodeSet.contains(x.admtng_icd9_dgns_cd)) {
          return true
        }

        for (s <- x.icd9_dgns_cds) {
          if (sputumCodeSet.contains(s)) {
            return true
          }
        }
      }
      for (x <- outPatientInfoThisLastYear) {

        if (sputumCodeSet.contains(x.admtng_icd9_dgns_cd)) {
          return true
        }

        for (s <- x.icd9_dgns_cds) {
          if (sputumCodeSet.contains(s)) {
            return true
          }
        }
      }

      return false
    }

    def getHL7InfoThisLastYear(): Boolean = {

      val hl7info = HL7.getRDD(msgBeneficiary.PartitionKeyData).filter { x =>
        originalFormat.parse(x.clm_thru_dt.toString()).before(today) && originalFormat.parse(x.clm_thru_dt.toString()).after(oneYearBeforeDate)
      }
      for (x <- hl7info) {

        if (x.chroniccough > 0 || x.sp_copd > 0 || x.shortnessofbreath > 0 || x.chronicsputum > 0) {
          return true
        }

      }
      return false
    }

    def getAATDeficiencyInLastYear(): Boolean = {
      val hl7info = HL7.getRDD(msgBeneficiary.PartitionKeyData).filter { x =>
        originalFormat.parse(x.clm_thru_dt.toString()).before(today) && originalFormat.parse(x.clm_thru_dt.toString()).after(oneYearBeforeDate)
      }
      for (x <- hl7info) {

        if (x.aatdeficiency == 1)
          return true

      }
      return false
    }

    def getCopdSymptoms(): Boolean = {
      if (getChronicSputumInLastYear || getChronicCoughInLastYear || getDyspnoeaInLastYear)
        return true

      return false
    }

    def getFamilyHistory(): Boolean = {

      if (msgBeneficiary.sp_copd == 1 || getHL7InfoThisLastYear)
        return true

      return false
    }

    def getInPatientClaimCostsByDate: Map[Int, Double] = {
      var inPatientClaimCostTuples = new ArrayList[Tuple2[Int, Double]]()
      for (x <- inPatientInfoThisLastyear) {
        inPatientClaimCostTuples.add((x.clm_thru_dt, x.clm_pmt_amt + x.nch_prmry_pyr_clm_pd_amt + x.clm_pass_thru_per_diem_amt + x.nch_bene_ip_ddctbl_amt + x.nch_bene_pta_coinsrnc_lblty_am + x.nch_bene_blood_ddctbl_lblty_am))
      }
      var inPatientClaimTotalCostEachDate = inPatientClaimCostTuples.groupBy(_._1).map { case (k, v) => (k, v.map(_._2)) }
      inPatientClaimTotalCostEachDate.map { case (k, v) => (k, v.sum) }
    }

    def getOutPatientClaimCostsByDate: Map[Int, Double] = {
      var outPatientClaimCostTuples = new ArrayList[Tuple2[Int, Double]]()
      for (x <- outPatientInfoThisLastYear) {
        outPatientClaimCostTuples.add((x.clm_thru_dt, x.clm_pmt_amt + x.nch_prmry_pyr_clm_pd_amt + x.nch_bene_blood_ddctbl_lblty_am + x.nch_bene_ptb_ddctbl_amt + x.nch_bene_ptb_coinsrnc_amt))
      }
      var outPatientClaimTotalCostEachDate = outPatientClaimCostTuples.groupBy(_._1).map { case (k, v) => (k, v.map(_._2)) }
      outPatientClaimTotalCostEachDate.map { case (k, v) => (k, v.sum) }
    }

    def getMaterializeOutputs: Boolean = {
      if (getInPatientClaimCostsByDate.size > 0 || getOutPatientClaimCostsByDate.size > 0)
        return true

      return false
    }

    def getCATII_Rule2: Boolean = {
      val birthDate = originalFormat.parse(msgBeneficiary.bene_birth_dt.toString())
      val ageInYears = Years.yearsBetween(new LocalDate(birthDate), new LocalDate(today)).getYears
      age = ageInYears
      if ((ageInYears > 40 && (getCopdSymptoms || getAATDeficiencyInLastYear || getFamilyHistory))) {
        return true
      }
      return false
    }

    def getCATI_Rule1b: Boolean = {
      val birthDate = originalFormat.parse(msgBeneficiary.bene_birth_dt.toString())
      val ageInYears = Years.yearsBetween(new LocalDate(birthDate), new LocalDate(today)).getYears
      age = ageInYears
      if (ageInYears > 40 && getOverSmokingCodesInLastYear && getAATDeficiencyInLastYear && getEnvironmentalExposuresInLastYear && getCopdSymptoms) {
        return true
      }
      return false
    }

    def getCATI_Rule1a: Boolean = {
      val birthDate = originalFormat.parse(msgBeneficiary.bene_birth_dt.toString())
      val ageInYears = Years.yearsBetween(new LocalDate(birthDate), new LocalDate(today)).getYears
      age = ageInYears
      if (ageInYears > 40 && getOverSmokingCodesInLastYear && (getAATDeficiencyInLastYear || getEnvironmentalExposuresInLastYear || getCopdSymptoms)) {
        return true
      }
      return false
    }

    println("Executing COPD Risk Assessment against message:");
    println("Message Type: " + msgBeneficiary.FullName)
    println("Message Name: " + msgBeneficiary.Name);
    println("Message Desynpuf ID: " + msgBeneficiary.desynpuf_id);

    if (getCATI_Rule1b) {
      var actualResults: Array[Result] = Array[Result](new Result("Risk Level:", "1b"),
        new Result("Age of the Benificiary:", age),
        new Result("Has Copd Symptoms?:", getCopdSymptoms.toString()),
        new Result("Has AAT Deficiency?:", getAATDeficiencyInLastYear.toString()),
        new Result("Has Family History?:", getFamilyHistory.toString),
        new Result("Has OverSmoking Codes?:", getOverSmokingCodesInLastYear.toString),
        new Result("Has Environmental Exposures?:", getEnvironmentalExposuresInLastYear.toString))
      return factory.createResultObject().asInstanceOf[MappedModelResults].withResults(actualResults)
    } else if (getCATI_Rule1a) {
      var actualResults: Array[Result] = Array[Result](new Result("Risk Level:", "1a"),
        new Result("Age of the Benificiary:", age),
        new Result("Has Copd Symptoms?:", getCopdSymptoms.toString()),
        new Result("Has AAT Deficiency?:", getAATDeficiencyInLastYear.toString()),
        new Result("Has Family History?:", getFamilyHistory.toString),
        new Result("Has OverSmoking Codes?:", getOverSmokingCodesInLastYear.toString),
        new Result("Has Environmental Exposures?:", getEnvironmentalExposuresInLastYear.toString))
      return factory.createResultObject().asInstanceOf[MappedModelResults].withResults(actualResults)
    } else if (getCATII_Rule2) {
      var actualResults: Array[Result] = Array[Result](new Result("Risk Level:", "2"),
        new Result("Age of the Benificiary:", age),
        new Result("Has Copd Symptoms?:", getCopdSymptoms.toString()),
        new Result("Has AAT Deficiency?:", getAATDeficiencyInLastYear.toString()),
        new Result("Has Family History?:", getFamilyHistory.toString),
        new Result("Has OverSmoking Codes?:", getOverSmokingCodesInLastYear.toString),
        new Result("Has Environmental Exposures?:", getEnvironmentalExposuresInLastYear.toString))
      return factory.createResultObject().asInstanceOf[MappedModelResults].withResults(actualResults)
    } else {

      return null
    }

  }
}