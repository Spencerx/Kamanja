package com.ligadata.kamanja.financial;

import com.google.common.base.Optional;
import com.ligadata.FatafatBase.*;
import com.ligadata.messagescontainers.System.*;

public class HelloWorldModel extends ModelBase {
	static HelloWorldModelObj objSingleton = new HelloWorldModelObj();
	ModelContext mdlCntxt;
	
	public HelloWorldModel(ModelContext mdlContext) {
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
    	
		msg1 helloWorld = (msg1) this.mdlCntxt.msg();
		if(helloWorld.score()!=1)
			return null;
    	
        Result[] actualResult = {new Result("Id",helloWorld.id()) , new Result("Name",helloWorld.name()), new Result("Score",helloWorld.score())};
        return new MappedModelResults().withResults(actualResult);
  }

   
	
    public static class HelloWorldModelObj implements ModelBaseObj {
		public boolean IsValidMessage(MessageContainerBase msg) {
			return (msg instanceof msg1);
		}

		public ModelBase CreateNewModel(ModelContext mdlContext) {
			return new HelloWorldModel(mdlContext);
		}

		public String ModelName() {
			return "HelloWorldModel";
		}

		public String Version() {
			return "0.0.1";
		}
		
		public ModelResultBase CreateResultObject() {
			return new MappedModelResults();
		}
	}

}
	
	
	
	
	
	
	
	


