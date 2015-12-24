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

import com.google.common.base.Optional;
import com.ligadata.KamanjaBase.*;
import com.ligadata.kamanja.metadata.ModelDef;

public class HelloWorldModel extends ModelInstance {
	public HelloWorldModel(ModelInstanceFactory factory) {
    	super(factory);
    }

	public ModelResultBase execute(TransactionContext txnCtxt, boolean outputDefault) {
    	/*
		System.out.println("inside model");
    	GlobalPreferences gPref = GlobalPreferencesFactory.rddObject.getRecentOrNew(new String[]{"PrefType"});  //(new String[]{"Type1"});
    	//System.out.println(gPref.);
    	CustPreferences cPref = (CustPreferences) CustPreferences.getRecentOrNew();
    	cPref.Save();
    	//GlobalPreferences gPref = GlobalPreferences.toJavaRDDObject().getRecentOrNew(new String[]{"Type1"});
    	//CustPreferences cPref = CustPreferences.toJavaRDDObject().getRecentOrNew();
    	System.out.println("Can persist:"+cPref.CanPersist());

    	if(cPref.minbalancealertoptout())
    	{
    		System.out.println("got minimum balance");
    		System.out.println(cPref.minbalancealertoptout());
    		return null;
    	}

    	RddDate curDtTmInMs = RddDate.currentGmtDateTime();
    	CustAlertHistory alertHistory = CustAlertHistory.toJavaRDDObject().getRecentOrNew(new String[]{"custId"});


    	if(curDtTmInMs.timeDiffInHrs(new RddDate(alertHistory.alertdttminms())) < gPref.minalertdurationinhrs())
    	{
    		return null;
    	}

    	TransactionMsg rcntTxn = (TransactionMsg) txnCtxt.getMessage();

    	 if (rcntTxn.balance() >= gPref.minalertbalance())
    	      return null;
		*/
		msg1 helloWorld = (msg1) txnCtxt.getMessage();
		if(helloWorld.score()!=1)
			return null;

        Result[] actualResult = {new Result("Id",helloWorld.id()) , new Result("Name",helloWorld.Name()), new Result("Score",helloWorld.score())};
        return new MappedModelResults().withResults(actualResult);
  }

    /**
     * @param inTxnContext
     */




    public static class HelloWorldModelFactory extends ModelInstanceFactory {
		public HelloWorldModelFactory(ModelDef modelDef, NodeContext nodeContext) {
			super(modelDef, nodeContext);
		}
		public boolean isValidMessage(MessageContainerBase msg) {
			return (msg instanceof msg1);
		}

		public ModelInstance createModelInstance() {
			return new HelloWorldModel(this);
		}

		public String getModelName() {
			return "HelloWorldModel";
		}

		public String getVersion() {
			return "0.0.1";
		}

		public ModelResultBase createResultObject() {
			return new MappedModelResults();
		}
	}

}










