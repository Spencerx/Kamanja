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

package com.ligadata.MetadataAPI

object ErrorCodeConstants {
   val Warning = 1;
   val Success = 0;
   val Failure = -1;
   val Not_Implemented_Yet = -2
   val File_Not_Found = "File not found";
   
   val Not_Implemented_Yet_Msg = "Not Implemented Yet";
  
  //Jar
   val Upload_Jar_Successful = "Uploaded Jar successfully";
   val Upload_Jar_Failed = "Failed to Upload the Jar";
  
  //Type
   val Add_Type_Successful = "Type added successfully";
   val Add_Type_Failed = "Failed to Add Type";
   val Remove_Type_Not_Found = "Failed to find type";
   val Remove_Type_Successful = "Deleted Type Sucessfully";
   val Remove_Type_Failed = "Failed to Delete Type";
   val Update_Type_Failed = "Failed to Update Type";
   val Update_Type_Internal_Error = "Update Type failed with an internal error";
   val Update_Type_Failed_Not_Found = "Failed to Update type object. There is no existing Type Available";
   val Update_Type_Failed_More_Than_One_Found = "Failed to Update type object. There are more than one latest Types Available";
   val Update_Type_Failed_Higher_Version_Required = "Failed to Update type object. New version must be greater than latest Available version";
   val Update_Type_Successful = "Type Updated Successfully";
   val Get_All_Types_Failed = "Failed to fetch all types";
   val Get_All_Types_Failed_Not_Available = "Failed to fetch all types. No Type Available";
   val Get_All_Types_Successful = "Successfully fetched all types";
   val Get_Type_Failed = "Failed to fetch type";
   val Get_Type_Failed_Not_Available = "Failed to fetch type. No Type Available.";
   val Get_Type_Successful = "Successfully fetched type";
   val Get_Type_Def_Failed = "Failed to fetch type def";
   val Get_Type_Def_Failed_Not_Available = "Failed to fetch type def. No Type Available.";
   val Get_Type_Def_Successful = "Successfully fetched type def";

  //Concept
   val Add_Concept_Successful = "Concept Added Successfully";
   val Add_Concept_Failed = "Failed to Add Concept";
   val Remove_Concept_Successful = "Deleted Concept Successfully";
   val Remove_Concept_Failed = "Failed to Delete Concept";
   val Remove_Concept_Failed_Not_Available = "Failed to Delete Concept. Concept not available";
   val Update_Concept_Successful = "Concept Updated Successfully";
   val Update_Concept_Failed = "Failed to Update Concept";
   val Get_All_Concepts_Failed = "Failed to fetch all concepts";
   val Get_All_Concepts_Failed_Not_Available = "Failed to fetch all concepts. No Concept Available.";
   val Get_All_Concepts_Successful = "Successfully fetched all concepts";
   val Get_Concept_Failed = "Failed to fetch concept";
   val Get_Concept_Failed_Not_Available = "Failed to fetch concept. No concept Available.";
   val Get_Concept_Successful = "Successfully fetched concept";
   val Get_All_Derived_Concepts_Failed = "Failed to Fetch Derived Concepts";
   val Get_All_Derived_Concepts_Failed_Not_Available = "Failed to Fetch Derived Concepts. No Derived Concept Available.";
   val Get_All_Derived_Concepts_Successful = "Successfully fetched all concepts";
   val Get_Derived_Concept_Failed = "Failed to fetch derived concept";
   val Get_Derived_Concept_Failed_Not_Available = "Failed to fetch derived concept. No Derived Concept available.";
   val Get_Derived_Concept_Successful = "Successfully fetched derived concepts";
  
  //Function
   val Add_Function_Successful = "Function Added Successfully";
   val Add_Function_Warning = "Some functions failed to update"
   val Add_Function_Failed = "Failed to Add Function";
   val Remove_Function_Successfully = "Deleted Function Successfully";
   val Remove_Function_Failed = "Failed to Delete Function";
   val Update_Function_Successful = "Function Updated Successfully";
   val Update_Function_Failed = "Failed to Update Fucntion";
   val Get_All_Functions_Failed = "Failed to fetch all containers";
   val Get_All_Functions_Failed_Not_Available = "Failed to fetch all containers. No function Available.";
   val Get_All_Functions_Successful = "Successfully fetched all containers";
   val Get_Function_Failed = "Failed to get function";
   val Get_Function_Failed_Not_Available = "Failed to get function. No Function Available.";
   val Get_Function_Successful = "Successfully fetched function";
  
  //Container
   val Add_Container_Successful = "Container Added Successfully";
   val Add_Container_Failed = "Failed to Add Container";
   val Update_Container_Successful = "Container Updated Successfully";
   val Update_Container_Failed = "Failed to Update Container";
   val Remove_Container_Failed = "Failed to Delete Container";
   val Remove_Container_Successful = "Deleted Container Successfully";
   val Remove_Container_Failed_Not_Found = "Failed to Delete Container. Container was not found";
   val Get_All_Containers_Failed = "Failed to fetch all containers";
   val Get_All_Containers_Failed_Not_Available = "Failed to fetch all containers. No container Available.";
   val Get_All_Containers_Successful = "All Containers fetched successfully from cache";
   val Get_Container_From_Cache_Failed = "Failed to fetch container from cache";
   val Get_Container_From_Cache_Successful = "Successfully fetched container from Cache";
   
   val Add_Container_Or_Message_Failed = "Failed to add container/message"
  
  //Message
   val Add_Message_Successful = "Message Added Successfully";
   val Add_Message_Failed = "Failed to Add Message";
   val Update_Message_Successful = "Message Updated Successfully";
   val Update_Message_Failed = "Failed to Update Message";
   val Remove_Message_Successful = "Deleted Message Successfully";
   val Remove_Message_Failed = "Failed to Delete Message";
   val Remove_Message_Failed_Not_Found = "Failed to Delete Message. Message Not found"
   val Get_All_Messages_Failed = "Failed to fetch all messages";
   val Get_All_Messages_Failed_Not_Available = "Failed to fetch all messages. No Message Available.";
   val Get_All_Messages_Succesful = "All Messages fetched successfully";
   val Get_Message_From_Cache_Failed = "Failed to fetch message from cache";
   val Get_Message_From_Cache_Successful = "Successfully fetched message from cache";
   val Recompile_Message_Failed = "Failed to recompile message";
   
   //Output Message
   val Add_OutputMessage_Successful = "Output Message Added Successfully";
   val Add_OutputMessage_Failed = "Failed to Add Output Message";
   val Update_OutputMessage_Successful = "Output Message Updated Successfully";
   val Update_OutputMessage_Failed = "Failed to Update Output Message";
   val Remove_OutputMessage_Successful = "Deleted Output Message Successfully";
   val Remove_OutputMessage_Failed = "Failed to Delete Output Message";
   val Remove_OutputMessage_Failed_Not_Found = "Failed to Delete Output Message. Output Message Not found"
   val Get_All_OutputMessages_Failed = "Failed to fetch all Output messages";
   val Get_All_OutputMessages_Failed_Not_Available = "Failed to fetch all Outputmessages. No Output Message Available.";
   val Get_All_OutputMessages_Succesful = "All Output Messages fetched successfully";
   val Get_OutputMessage_From_Cache_Failed = "Failed to fetch Output message from cache";
   val Get_OutputMessage_From_Cache_Successful = "Successfully fetched Output message from cache";
   val Recompile_OutputMessage_Failed = "Failed to recompile Output message";
   
  //Model
   val Deactivate_Model_Successful = "Deactivated Model Successfully";
   val Deactivate_Model_Failed = "Failed to Deactivate Model";
   val Deactivate_Model_Failed_Not_Active = "Failed to Deactivate Model. Model may not be active"
   val Activate_Model_Successful = "Activated Model Successfully";
   val Activate_Model_Failed = "Failed to Activate Model";
   val Activate_Model_Failed_Not_Active = "Failed to Activate Mode. Model may not be active"
   val Remove_Model_Failed = "Failed to Delete Model";
   val Remove_Model_Failed_Not_Found = "Failed to Delete the Model. Model was not found."
   val Remove_Model_Successful = "Deleted Model Successfully";
   val Add_Model_Successful = "Model Added Successfully";
   val Add_Model_Failed = "Failed to Add Model";
   val Add_Model_Failed_Higher_Version_Required = "Failed to Add Model. Model with higher version required than any previous version including removed models"
   val Update_Model_Failed = "Failed to Update Model";
   val Update_Model_Failed_Invalid_Version = "Failed to Update Model. Invalid Version.";
   val Get_All_Models_Failed = "Failed to fetch all models";
   val Get_All_Models_Failed_Not_Available = "Failed to fetch all models. No Model available.";
   val Get_All_Models_Successful = "All Models fetched successfully";
   val Get_Model_Failed = "Failed to get model";
   val Get_Model_Failed_Not_Available = "Failed to get model. No Model available.";
   val Get_Model_Successful = "Fetched Model successfully";
   val Get_Model_From_Cache_Failed_Not_Active = "Failed to fetch model. Model may not be active"
   val Get_Model_From_Cache_Successful = "Successfully fetched Model from Cache";
   val Get_Model_From_Cache_Failed = "Failed to fetch model from cache";
   val Get_Model_From_DB_Failed = "Failed to fetch Model from DB";
   val Get_Model_From_DB_Successful = "Successfully fetched Model form DB";
   val Model_Compilation_Failed = "Model Compilation Failed, see compliation errors in the log"
   
   // Functions
   val Remove_Function_Failed_Not_Found = "Failed to delete an existing Function"
   
   //Node
   val Add_Node_Failed = "Failed to add/update a node";
   val Add_Node_Successful = "Node added/updated Successfully";
   val Remove_Node_Successful = "Deleted node successfully";
   val Remove_Node_Failed = "Failed to delete node";
   val Get_All_Nodes_Successful = "Successfuly fetched all nodes";
   val Get_All_Nodes_Failed = "Failed to fetch all nodes";
   val Get_All_Nodes_Failed_Not_Available = "Failed to fetch all nodes. No node available.";
   val Get_Leader_Host_Failed_Not_Available = "Failed to fetch leader host. No node available.";
   
   //Adapter
   val Add_Adapter_Failed = "Failed to Add/Update Adapter";
   val Add_Adapter_Successful = "Successfully added/updated Adapter";
   val Remove_Adapter_Failed = "Failed to delete an adapter";
   val Remove_Adapter_Successful = "Deleted adapter successfully";
   val Get_All_Adapters_Failed= "Failed to fetch all adapters";
   val Get_All_Adapters_Failed_Not_Available = "Failed to fetch all adapters. No Adapter available.";
   val Get_All_Adapters_Successful = "Successfully fetched all adpaters";
   
   //Cluster
   val Add_Cluster_Failed = "Failed to add/update cluster";
   val Add_Cluster_Successful = "Cluster added/updated successfully";
   val Remove_Cluster_Failed = "Failed to delete cluster";
   val Remove_Cluster_Successful = "Deleted cluster successfully";
   val Add_Cluster_Config_Failed = "Failed to add cluster";
   val Add_Cluster_Config_Successful = "Cluster added successfully";
   val Remove_Cluster_Config_Failed = "Failed to delete cluster config";
   val Remove_Cluster_Config_Successful = "Deleted Cluster config successfully";
   val Get_All_Clusters_Failed = "Failed to fetch all clusters";
   val Get_All_Clusters_Failed_Not_Available = "Failed to fetch all clusters. No cluster available.";
   val Get_All_Clusters_Successful = "Successfuly fetched all clusters";
   val Get_All_Cluster_Configs_Failed = "Failed to fetch all cluster configs";
   val Get_All_Cluster_Configs_Failed_Not_Available = "Failed to fetch all cluster configs. No cluster config available.";
   val Get_All_Cluster_Configs_Successful = "Successfully fetched all Cluster Configs ";
   
   //Config
   val Remove_Config_Failed = "Failed to delete config";
   val Remove_Config_Successful = "Deleted Config successfully";
   val Upload_Config_Failed = "Failed to upload config";
   val Upload_Config_Successful = "Uploaded Config successfully";
   val Get_All_Configs_Failed = "Failed to fetch all configs";
   val Get_All_Configs_Failed_Not_Available = "Failed to fetch all configs. No configs available.";
   val Get_All_Configs_Successful = "Successfully fetched all configs";
   
   //Object
   val Get_All_Object_Keys_Successful = "Successfully fetched all object keys";
   
   //Monitor
   val GetHeartbeat_Success = "Heartbeats fetched successfully"
   val GetHeartbeat_Failed = "Get Heartbeats Failed"

}
