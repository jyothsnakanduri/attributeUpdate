package com.utility.tools;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.utility.tools.RtcConfigValueHolder;
import com.ibm.team.filesystem.client.internal.rest.util.LoginUtil.LoginHandler;
import com.ibm.team.process.client.IProcessClientService;
import com.ibm.team.process.client.IProcessItemService;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.TeamPlatform;
import com.ibm.team.repository.common.IContributorHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
//import com.ibm.team.workitem.api.common.IAttribute;
import com.ibm.team.workitem.api.common.WorkItemAttributes;
import com.ibm.team.workitem.client.IAuditableClient;
import com.ibm.team.workitem.client.IWorkItemClient;
import com.ibm.team.workitem.client.IWorkItemWorkingCopyManager;
import com.ibm.team.workitem.client.WorkItemWorkingCopy;
import com.ibm.team.workitem.common.IWorkItemCommon;
import com.ibm.team.workitem.common.model.IAttribute;
import com.ibm.team.workitem.common.model.IAttributeHandle;
import com.ibm.team.workitem.common.model.IEnumeration;
import com.ibm.team.workitem.common.model.ILiteral;
import com.ibm.team.workitem.common.model.IWorkItem;
import com.ibm.team.workitem.common.model.Identifier;
import com.ibm.team.workitem.common.model.ItemProfile;

public class AttributeUpdater {

	private static final IProgressMonitor monitor = null;
	static{
		System.out.println("Attribute Updater");
	}
	static Properties prop;
	private static IAuditableClient auditableClient;
	static 	ITeamRepository rtcRepository;
	static RtcConfigValueHolder rtcConfigHolder;
	//static IWorkItemClient workItemClient;
	IWorkItemClient workItemClient = (IWorkItemClient) rtcRepository.getClientLibrary(IWorkItemClient.class); 
	static IProjectArea prjArea ;
	private static IProgressMonitor nullProgressMonitor = new NullProgressMonitor();

	static Map<String,IContributorHandle> cacheContributorMap = new HashMap<String,IContributorHandle>();
	static{
		prop = new Properties();
		InputStream input = null;
		try{

			input= AttributeUpdater.class.getClassLoader().getResourceAsStream("rtc_config.properties");

			// load a properties file
			prop.load(input);
			loadProperties();
		}catch(Exception e){
			System.out.println("Error in loading properties file,exiting");
			System.exit(1);
		}
		finally{
			try {
				input.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static void loginToRTCServer(){
		TeamPlatform.startup();


		try {
			System.out.println(" Logging in to RTC");
			rtcRepository = TeamPlatform.getTeamRepositoryService().getTeamRepository(rtcConfigHolder.getRtcUrl());

			rtcRepository.registerLoginHandler(new LoginHandler(rtcConfigHolder.getUserName(),rtcConfigHolder.getPassword()));

			System.out.println("Repository" + rtcRepository.getRepositoryURI());
			System.out.println(" User name " + rtcConfigHolder.getUserName());
			if (rtcRepository != null && !rtcRepository.loggedIn()) {

				rtcRepository.login(null);
			}

			auditableClient = (IAuditableClient)rtcRepository.getClientLibrary(IAuditableClient.class);
			System.out.println("Logged In to RTC start up completed ");
		} catch (TeamRepositoryException e) {
			e.printStackTrace();
		}

	}

	public static void loadProperties() {

		System.out.println(" Enter in to Main");

		try{

			if(prop!=null){
				rtcConfigHolder=new RtcConfigValueHolder();
				rtcConfigHolder.setRtcUrl(prop.getProperty("rtc.Url"));
				rtcConfigHolder.setUserName(prop.getProperty("username"));
				rtcConfigHolder.setPassword(prop.getProperty("password"));
				rtcConfigHolder.setProjectArea(prop.getProperty("project.area"));
				loginToRTCServer();
				prjArea = findProjectArea(prop.getProperty("project.area"));
			}

		}catch(Exception e){
			System.out.println("Error in process 2 "+e.getMessage());
			e.printStackTrace();
		}


	}

	private static  IProjectArea  findProjectArea(String projectName)throws TeamRepositoryException {
		IProcessItemService service = (IProcessItemService) rtcRepository.getClientLibrary(IProcessItemService.class);

		IProjectArea area= null ;

		List areas = service.findAllProjectAreas(
				IProcessClientService.ALL_PROPERTIES, nullProgressMonitor);

		for (Object anArea : areas) {
			if (anArea instanceof IProjectArea) {
				IProjectArea foundArea = (IProjectArea) anArea;    
				System.out.println(" list of areas" + foundArea.getName()+ " projectName"+ projectName);

				if (foundArea.getName().equals(projectName)) {
					System.out.println("Project found: " + projectName);               
					return foundArea;
				}
			}
		}
		return null;

	}

	private   IWorkItem  findWorkItemTypeById(String WorkItemID) throws TeamRepositoryException {
		IWorkItemCommon common = (IWorkItemCommon) rtcRepository.getClientLibrary(IWorkItemCommon.class); 
		int id = new Integer(WorkItemID).intValue();
		IWorkItem workItem = common.findWorkItemById(id,IWorkItem.SMALL_PROFILE, monitor); 
		if (workItem == null) {
			System.out.println("Work item: " + WorkItemID + " not found."); 
		}

		return workItem;
	}

	private   IAttribute findAttributeByName( String iAttributeName, IProjectArea foundArea) throws TeamRepositoryException {
		System.out.println(" FoundArea " + foundArea);
		System.out.println(" Attribute name " + iAttributeName);
	
		System.out.println(" work Item Client " +workItemClient );
		List allAttributeHandles = workItemClient.findAttributes(foundArea  , monitor);
		for (Iterator iterator = allAttributeHandles .iterator(); iterator.hasNext();) {  
			IAttributeHandle iAttributeHandle = (IAttributeHandle) iterator.next();   
			IAttribute iAttribute = (IAttribute) rtcRepository.itemManager().fetchCompleteItem(iAttributeHandle, IItemManager.DEFAULT ,monitor); 
			if(iAttribute.getDisplayName().equalsIgnoreCase(iAttributeName)){
				return iAttribute;
			}
		}

		return null;
	}

	public void updateAttributeValue(IAttribute attribute,Object value,IWorkItem workItem){

		IWorkItemWorkingCopyManager wcm = workItemClient.getWorkItemWorkingCopyManager();

		try {
			wcm.connect(workItem, IWorkItem.FULL_PROFILE, nullProgressMonitor);
			WorkItemWorkingCopy workingCopy = wcm.getWorkingCopy(workItem);
			IWorkItem workItemFromCopy = workingCopy.getWorkItem();
			workItemFromCopy.setValue(attribute,value);
			
			workingCopy.save(nullProgressMonitor);

		} catch (TeamRepositoryException tre) {
			tre.printStackTrace();
		} finally {
			wcm.disconnect(workItem);
		}
	}
	
	public  Identifier getLiteralbyValue(String lieralValue, IAttribute iAttribute) throws TeamRepositoryException {
		IEnumeration enumeration = workItemClient.resolveEnumeration(iAttribute, null);

		List literals = enumeration.getEnumerationLiterals();
		for (Iterator iterator = literals.iterator(); iterator.hasNext();) {
			ILiteral iLiteral = (ILiteral) iterator.next();
			if (iLiteral.getName().equalsIgnoreCase(lieralValue)) {
				return iLiteral.getIdentifier2();
			}
		}
		return null;
	}
	
	public static void main(String[] args) throws TeamRepositoryException {
		if (args.length != 3) {
			System.out.println("Usage: AttributeUpdate <WorkItemID> <AtributeName> <AtributeValue> ");
		}

		String workItemID= args[0];
		String atributeName= args[1];
		String atributeValue= args[2];
		AttributeUpdater attributeupdater = new AttributeUpdater() ;
		IWorkItem workItem = attributeupdater.findWorkItemTypeById(workItemID);
		IAttribute attributeIns = attributeupdater.findAttributeByName(atributeName, prjArea);
		if( attributeIns == null) {
			System.out.println(" Invalid attribute name  or attribute not found ");
			System.exit(0);
		}
		Object attrValue = workItem.getValue(attributeIns);
		if (attrValue instanceof Identifier) {
			attrValue = attributeupdater.getLiteralbyValue(atributeValue,attributeIns);
		} else {
			attrValue = atributeValue;
		}
		attributeupdater.updateAttributeValue(attributeIns, attrValue, workItem);
		System.out.println("======== Update Completed ===========");
		System.exit(0);
	}
}
