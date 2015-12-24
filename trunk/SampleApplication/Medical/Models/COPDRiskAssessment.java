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

package com.ligadata.kamanja.samples.models;

import com.ligadata.KamanjaBase.api.java.function.Function1;
import com.ligadata.KamanjaBase.*;
import org.joda.time.*;
import com.ligadata.kamanja.metadata.ModelDef;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class COPDRiskAssessment extends ModelInstance {
    public COPDRiskAssessment(ModelInstanceFactory factory) {
        super(factory);
    }

    public static class COPDRiskAssessmentFactory extends ModelInstanceFactory {
		public COPDRiskAssessmentFactory(ModelDef modelDef, NodeContext nodeContext) {
			super(modelDef, nodeContext);
		}

        public boolean isValidMessage(MessageContainerBase msg) {
            return (msg instanceof Beneficiary);
        }

        public ModelInstance createModelInstance() {
            return new COPDRiskAssessment(this);
        }

        public String getModelName() {
            return "COPDRiskAssessment";
        }

        public String getVersion() {
            return "0.0.1";
        }

        public ModelResultBase createResultObject() {
            return new MappedModelResults();
        }

    }

    private class FilterClaims<T extends BaseMsg> implements Function1<T, Boolean> {
        @Override
        public Boolean call(T claim) throws Exception {
            if(claim instanceof InpatientClaim){
                return isDateBetween(((InpatientClaim) claim).clm_thru_dt());
            }
            else if(claim instanceof OutpatientClaim) {
                return isDateBetween(((OutpatientClaim) claim).clm_thru_dt());
            }
            else if(claim instanceof HL7) {
                return isDateBetween(((HL7) claim).clm_thru_dt());
            }
            else {
                System.out.println("Unknown type '" + claim.getClass() + "'");
                return false;
            }
        }
    }

    private Beneficiary msg = null;

    // Filtered Message Arrays
    private ArrayList<InpatientClaim> inpatientClaimHistory = new ArrayList<>();
    private ArrayList<OutpatientClaim> outpatientClaimHistory = new ArrayList<>();
    private ArrayList<HL7> hl7History = new ArrayList<>();

    // Lookup Arrays
    private ArrayList<String> coughCodes = new ArrayList<>();
    private ArrayList<String> dyspnoeaCodes = new ArrayList<>();
    private ArrayList<String> envCodes = new ArrayList<>();
    private ArrayList<String> smokeCodes = new ArrayList<>();
    private ArrayList<String> sputumCodes = new ArrayList<>();

    private SimpleDateFormat yearMonthDayHourFormat = new SimpleDateFormat("yyyyMMdd");

    private void init(TransactionContext txnCtxt) {
        msg = (Beneficiary) txnCtxt.getMessage();
        System.out.println("Executing COPD Risk Assessment against Beneficiary message:");
        System.out.println("\tMessage Name: " + msg.Name());
        System.out.println("\tMessage Version: " + msg.Version());
        System.out.println("\tMessage Desynpuf ID: " + msg.desynpuf_id());

        String[] partitionKeys = msg.PartitionKeyData();

        // Getting message RDD objects
        JavaRDD<InpatientClaim> inpatientClaimHistoryRDD = InpatientClaimFactory.rddObject.getRDD(partitionKeys).filter(new FilterClaims());
        JavaRDD<OutpatientClaim> outpatientClaimHistoryRDD = OutpatientClaimFactory.rddObject.getRDD(partitionKeys).filter(new FilterClaims());
        JavaRDD<HL7> hl7HistoryRDD = HL7Factory.rddObject.getRDD(partitionKeys).filter(new FilterClaims());

        // Getting container RDD objects
        JavaRDD<CoughCodes> coughCodesRDD = CoughCodesFactory.rddObject.getRDD();
        JavaRDD<DyspnoeaCodes> dyspnoeaCodesRDD = DyspnoeaCodesFactory.rddObject.getRDD();
        JavaRDD<EnvCodes> envCodesRDD = EnvCodesFactory.rddObject.getRDD();
        JavaRDD<SmokeCodes> smokeCodesRDD = SmokeCodesFactory.rddObject.getRDD();
        JavaRDD<SputumCodes> sputumCodesRDD = SputumCodesFactory.rddObject.getRDD();

        // Taking all messages from the JavaRDD's iterator and placing them in an ArrayList
        for (Iterator<InpatientClaim> ipClaimIt = inpatientClaimHistoryRDD.iterator(); ipClaimIt.hasNext();) {
            inpatientClaimHistory.add(ipClaimIt.next());
        }

        for (Iterator<OutpatientClaim> opClaimIt = outpatientClaimHistoryRDD.iterator(); opClaimIt.hasNext(); ) {
            outpatientClaimHistory.add(opClaimIt.next());
        }

        for (Iterator<HL7> hl7Iterator = hl7HistoryRDD.iterator(); hl7Iterator.hasNext(); ) {
            hl7History.add(hl7Iterator.next());
        }

        // Taking all icd9 codes from the containers and placing them in an ArrayList
        for (Iterator<CoughCodes> coughCodeIt = coughCodesRDD.iterator(); coughCodeIt.hasNext(); ) {
            coughCodes.add(coughCodeIt.next().icd9code());
        }

        for (Iterator<DyspnoeaCodes> dyspCodeIt = dyspnoeaCodesRDD.iterator(); dyspCodeIt.hasNext(); ) {
            dyspnoeaCodes.add(dyspCodeIt.next().icd9code());
        }

        for (Iterator<EnvCodes> envCodeIt = envCodesRDD.iterator(); envCodeIt.hasNext(); ) {
            envCodes.add(envCodeIt.next().icd9code());
        }

        for (Iterator<SmokeCodes> smokeCodeIt = smokeCodesRDD.iterator(); smokeCodeIt.hasNext(); ) {
            smokeCodes.add(smokeCodeIt.next().icd9code());
        }

        for (Iterator<SputumCodes> sputumCodeIt = sputumCodesRDD.iterator(); sputumCodeIt.hasNext(); ) {
            sputumCodes.add(sputumCodeIt.next().icd9code());
        }
    }

    private boolean isDateBetween(Integer date) throws ParseException {
        Calendar calendar = Calendar.getInstance();
        Date tDate = yearMonthDayHourFormat.parse(date.toString());
        Date today = calendar.getTime();
        calendar.add(Calendar.YEAR, -1);
        Date oneYearAgo = calendar.getTime();
        return ((tDate.before(today) || tDate.equals(today)) && (tDate.after(oneYearAgo) || tDate.equals(oneYearAgo)));
    }

    private Boolean age40OrOlder() {
        org.joda.time.LocalDate birthdate = new org.joda.time.LocalDate(msg.bene_birth_dt() / 10000, (msg.bene_birth_dt() % 1000) / 100, msg.bene_birth_dt() % 100);
        Integer age = Years.yearsBetween(birthdate, new LocalDate()).getYears();
        if (age > 40) {
            return true;
        }
        return false;
    }

    private Boolean hasSmokingHistory() {
        for (InpatientClaim ic : inpatientClaimHistory) {
            if (smokeCodes.contains(ic.admtng_icd9_dgns_cd()))
                return true;

            for (String code : ic.icd9_dgns_cds()) {
                if (smokeCodes.contains(code))
                    return true;
            }
        }

        for (OutpatientClaim oc : outpatientClaimHistory) {
            for(String code: oc.icd9_dgns_cds()){
                System.out.println("\t" + code);
            }
            if (smokeCodes.contains(oc.admtng_icd9_dgns_cd())) {
                return true;
            }

            for (String code : oc.icd9_dgns_cds()) {
                if (smokeCodes.contains(code)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Boolean hasEnvironmentalExposure() {
        for (InpatientClaim ic : inpatientClaimHistory) {
            if (envCodes.contains(ic.admtng_icd9_dgns_cd())) {
                return true;
            }

            for (String code : ic.icd9_dgns_cds()) {
                if (envCodes.contains(code)) {
                    return true;
                }
            }
        }

        for (OutpatientClaim oc : outpatientClaimHistory) {
            if (envCodes.contains(oc.admtng_icd9_dgns_cd())) {
                return true;
            }

            for (String code : oc.icd9_dgns_cds()) {
                if (envCodes.contains(code)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Boolean hasDyspnea() {
        for (InpatientClaim ic : inpatientClaimHistory) {
            if (dyspnoeaCodes.contains(ic.admtng_icd9_dgns_cd())) {
                return true;
            }

            for (String code : ic.icd9_dgns_cds()) {
                if (dyspnoeaCodes.contains(code)) {
                    return true;
                }
            }
        }

        for (OutpatientClaim oc : outpatientClaimHistory) {
            if (dyspnoeaCodes.contains(oc.admtng_icd9_dgns_cd())) {
                return true;
            }

            for (String code : oc.icd9_dgns_cds()) {
                if (dyspnoeaCodes.contains(code)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Boolean hasChronicCough() {
        for (InpatientClaim ic : inpatientClaimHistory) {
            if (coughCodes.contains(ic.admtng_icd9_dgns_cd())) {
                return true;
            }

            for (String code : ic.icd9_dgns_cds()) {
                if (coughCodes.contains(code)) {
                    return true;
                }
            }
        }

        for (OutpatientClaim oc : outpatientClaimHistory) {
            if (coughCodes.contains(oc.admtng_icd9_dgns_cd())) {
                return true;
            }

            for (String code : oc.icd9_dgns_cds()) {
                if (coughCodes.contains(code)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Boolean hasChronicSputum() {
        for (InpatientClaim ic : inpatientClaimHistory) {
            if (sputumCodes.contains(ic.admtng_icd9_dgns_cd())) {
                return true;
            }

            for (String code : ic.icd9_dgns_cds()) {
                if (sputumCodes.contains(code)) {
                    return true;
                }
            }
        }

        for (OutpatientClaim oc : outpatientClaimHistory) {
            if (sputumCodes.contains(oc.admtng_icd9_dgns_cd())) {
                return true;
            }

            for (String code : oc.icd9_dgns_cds()) {
                if (sputumCodes.contains(code)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Boolean hasAATDeficiency() {
        for (HL7 hl7 : hl7History) {
            if (hl7.aatdeficiency() == 1) {
                return true;
            }
        }
        return false;
    }

    private Boolean hasFamilyHistory() {
        if (msg.sp_copd() == 1) {
            return true;
        }

        for (HL7 hl7 : hl7History) {
            if (hl7.chroniccough() > 0 || hl7.sp_copd() > 0 || hl7.shortnessofbreath() > 0 || hl7.chronicsputum() > 0) {
                return true;
            }
        }
        return false;
    }

    private double inpatientClaimCosts() {
        double totalCost = 0;

        for (InpatientClaim claim : inpatientClaimHistory) {
            totalCost += claim.clm_pmt_amt() + claim.nch_prmry_pyr_clm_pd_amt() + claim.clm_pass_thru_per_diem_amt() +
                    claim.nch_bene_ip_ddctbl_amt() + claim.nch_bene_pta_coinsrnc_lblty_am() + claim.nch_bene_blood_ddctbl_lblty_am();
        }
        return totalCost;
    }

    private double outpatientClaimCosts() {
        double totalCost = 0d;
        for (OutpatientClaim claim : outpatientClaimHistory) {
            totalCost += claim.clm_pmt_amt() + claim.nch_prmry_pyr_clm_pd_amt() + claim.nch_bene_blood_ddctbl_lblty_am() +
                    claim.nch_bene_ptb_ddctbl_amt() + claim.nch_bene_ptb_coinsrnc_amt();
        }
        return totalCost;
    }


    private MappedModelResults copdRiskLevel() {
        Boolean hasSmokingHistory = hasSmokingHistory();
        Boolean hasEnvironmentalExposure = hasEnvironmentalExposure();
        Boolean hasDyspnea = hasDyspnea();
        Boolean hasChronicCough = hasChronicCough();
        Boolean hasChronicSputum = hasChronicSputum();
        Boolean hasAATDeficiency = hasAATDeficiency();
        Boolean hasFamilyHistory = hasFamilyHistory();
        Boolean ageOver40 = age40OrOlder();

        Boolean hasCOPDSymptoms = hasDyspnea || hasChronicCough || hasChronicSputum;

        String riskLevel = "";

        if (ageOver40 && hasSmokingHistory && hasAATDeficiency && hasEnvironmentalExposure && hasCOPDSymptoms) {
            riskLevel = "1b";
        } else if (ageOver40 && hasSmokingHistory && (hasAATDeficiency || hasEnvironmentalExposure || hasCOPDSymptoms)) {
            riskLevel = "1a";
        } else if (!ageOver40 && (hasCOPDSymptoms || hasAATDeficiency || hasFamilyHistory)) {
            riskLevel = "2";
        }

        Result[] results = new Result[]{
                new Result("Desynpuf ID", msg.desynpuf_id()),
                new Result("COPD Risk Level", riskLevel),
                new Result("Is Over 40 Years Old", ageOver40),
                new Result("Has Smoking History", hasSmokingHistory),
                new Result("Has Environmental Exposure", hasEnvironmentalExposure),
                new Result("Has Dyspnea", hasDyspnea),
                new Result("Has Chronic Cough", hasChronicCough),
                new Result("Has Chronic Sputum", hasChronicSputum),
                new Result("Has AAT Deficiency", hasAATDeficiency),
                new Result("Inpatient Claim Costs", inpatientClaimCosts()),
                new Result("Outpatient Claim Costs", outpatientClaimCosts())
        };

        for (Result result : results) {
            System.out.println(result.name() + ": " + result.result());
        }
        System.out.println("******************************************************************************");

        return ((MappedModelResults) factory().createResultObject()).withResults(results);
    }

    @Override
    public MappedModelResults execute(TransactionContext txnCtxt, boolean outputDefault) {
        init(txnCtxt);
        MappedModelResults result = copdRiskLevel();
        if(!outputDefault) {
            if (result.get("COPD Risk Level") == "") {
                return null;
            }
        }
        return result;
    }
}