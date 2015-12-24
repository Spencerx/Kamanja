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

package com.ligadata.samples.models;

import com.ligadata.KamanjaBase.*;
import com.ligadata.kamanja.metadata.ModelDef;
import com.ligadata.samples.messages.*;
import com.google.common.base.Optional;
import com.ligadata.kamanja.metadata.ModelDef;

public class JavaTestMdl extends ModelInstance {
	public ModelResultBase execute(TransactionContext txnCtxt, boolean outputDefault) {
		// Directly calling methods from Scala Singleton object. Not preferable
		// to use direct scala.
		CustAlertHistory custAlertHistory = CustAlertHistoryFactory.rddObject.getRecentOrNew();

		// Getting Java RDD Object and performing operations on that
		JavaRDDObject<CustAlertHistory> javaRddObj = CustAlertHistoryFactory.rddObject;
		Optional<CustAlertHistory> obj = javaRddObj.getRecent();

		if (obj.isPresent()) {

		} else {

		}

		return null;
	}

	public JavaTestMdl(ModelInstanceFactory factory) {
		super(factory);
	}

	public static class JavaTestMdlFactory extends ModelInstanceFactory {
		public JavaTestMdlFactory(ModelDef mdlDef, NodeContext nodeContext) {
			super(mdlDef, nodeContext);
		}

		public boolean isValidMessage(MessageContainerBase msg) {
			return (msg instanceof CustAlertHistory);
		}

		public ModelInstance createModelInstance() {
			return new JavaTestMdl(this);
		}

		public String getModelName() {
			return "JavaTestMdl";
		}

		public String getVersion() {
			return "0.0.1";
		}

		public ModelResultBase createResultObject() {
			return new MappedModelResults();
		}
	}
}