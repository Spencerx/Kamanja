package com.ligadata.kamanja.financial;

import scala.Option;

import com.ligadata.FatafatBase.*;
import com.ligadata.messagescontainers.System.*;

public class LowBalanceAlertModel extends ModelBase {
	static LowBalanceAlertModelObj objSingleton = new LowBalanceAlertModelObj();
	ModelContext mdlCntxt;
	
	public LowBalanceAlertModel(ModelContext mdlContext) {
    	super(mdlContext, objSingleton);
    	mdlCntxt = mdlContext;
    }

	
	@Override
	public ModelBaseObj factory() {
		// TODO Auto-generated method stub
		return objSingleton;
	}

	@Override
	public ModelContext modelContext() {
		// TODO Auto-generated method stub
		return mdlCntxt;
	}

	public ModelResultBase execute(boolean emitAllResults) {
    	
    	GlobalPreferences gPref = (GlobalPreferences) GlobalPreferences.getRecentOrNew(new String[]{"Type1"});  //(new String[]{"Type1"});
    	
    	CustPreferences cPref = (CustPreferences) CustPreferences.getRecentOrNew();
    	
    	
    	
    	if(cPref.minbalancealertoptout())
    	{
    		return null;
    	}
    	
    	RddDate curDtTmInMs = RddDate.currentGmtDateTime();
    	CustAlertHistory alertHistory = (CustAlertHistory) CustAlertHistory.getRecentOrNew();
    	
    	if(curDtTmInMs.timeDiffInHrs(new RddDate(alertHistory.alertdttminms())) < gPref.minalertdurationinhrs())
    	{
    		return null;
    	}
    	
    	TransactionMsg rcntTxn = (TransactionMsg) this.modelContext().msg();
    	
    	 if (rcntTxn.balance() >= gPref.minalertbalance())
    	      return null;
		
    	    long curTmInMs = curDtTmInMs.getDateTimeInMs();
    	    	    // create new alert history record and persist (if policy is to keep only one, this will replace existing one)
    	    	    CustAlertHistory.build().withalertdttminms(curTmInMs).withalerttype("lowbalancealert").Save();
    	
    	
        Result[] actualResult = {new Result("Customer ID",rcntTxn.custid()),
        						 new Result("Branch ID",rcntTxn.branchid()),
        						 new Result("Account No.",rcntTxn.accno()),
        						 new Result("Current Balance",rcntTxn.balance()),
        						 new Result("Alert Type","lowbalancealert"),
        						 new Result("Trigger Time",new RddDate(curTmInMs).toString())
        };
        return new MappedModelResults().withResults(actualResult);
  }

    /**
     * @param inTxnContext
     */ 
    
    
    public boolean minBalanceAlertOptout(JavaRDD<CustPreferences> pref)
    {
    	
    	return false;
    }
	
    public static class LowBalanceAlertModelObj implements ModelBaseObj {
		public boolean IsValidMessage(MessageContainerBase msg) {
			return (msg instanceof TransactionMsg);
		}

		public ModelBase CreateNewModel(ModelContext mdlContext) {
			return new LowBalanceAlertModel(mdlContext);
		}

		public String ModelName() {
			return "LowBalanceAlert";
		}

		public String Version() {
			return "0.0.1";
		}
		
		public ModelResultBase CreateResultObject() {
			return new MappedModelResults();
		}
	}

}
	
	
	
	
	
	
	
	


