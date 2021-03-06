/**
 * Copyright &copy; 2012-2016 <a href="https://gitee.com/JeeHuangBingGui/jeeSpringCloud">JeeSpring</a> All rights reserved.
 */
package com.jeespring.modules.act.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.engine.FormService;
import org.activiti.engine.HistoryService;
import org.activiti.engine.IdentityService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.history.HistoricTaskInstanceQuery;
import org.activiti.engine.impl.RepositoryServiceImpl;
import org.activiti.engine.impl.RuntimeServiceImpl;
import org.activiti.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.identity.Authentication;
import org.activiti.engine.impl.interceptor.CommandExecutor;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntity;
import org.activiti.engine.impl.pvm.delegate.ActivityBehavior;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.impl.task.TaskDefinition;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.repository.ProcessDefinitionQuery;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Comment;
import org.activiti.engine.task.Task;
import org.activiti.engine.task.TaskQuery;
import org.activiti.spring.ProcessEngineFactoryBean;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jeespring.common.persistence.Page;
import com.jeespring.common.service.AbstractService;
import com.jeespring.common.utils.StringUtils;
import com.jeespring.modules.act.dao.ActDao;
import com.jeespring.modules.act.entity.Act;
import com.jeespring.modules.act.service.cmd.CreateAndTakeTransitionCmd;
import com.jeespring.modules.act.service.cmd.JumpTaskCmd;
import com.jeespring.modules.act.service.creator.ChainedActivitiesCreator;
import com.jeespring.modules.act.service.creator.MultiInstanceActivityCreator;
import com.jeespring.modules.act.service.creator.RuntimeActivityDefinitionEntityIntepreter;
import com.jeespring.modules.act.service.creator.SimpleRuntimeActivityDefinitionEntity;
import com.jeespring.modules.act.utils.ActUtils;
import com.jeespring.modules.act.utils.ProcessDefCache;
import com.jeespring.modules.act.utils.ProcessDefUtils;
import com.jeespring.modules.sys.entity.User;
import com.jeespring.modules.sys.utils.UserUtils;

/**
 * ??????????????????Service
 * @author JeeSpring
 * @version 2013-11-03
 */
@Service
@Transactional(readOnly = true)
public class ActTaskService extends AbstractService {

	@Autowired
	private ActDao actDao;

	@Autowired
	private ProcessEngineFactoryBean processEngineFactory;
	
	@Autowired
	private ProcessEngine processEngine;
	@Autowired
	private RuntimeService runtimeService;
	@Autowired
	private TaskService taskService;
	@Autowired
	private FormService formService;
	@Autowired
	private HistoryService historyService;
	@Autowired
	private RepositoryService repositoryService;
	@Autowired
	private IdentityService identityService;
	
	/**
	 * ??????????????????
	 * @param procDefKey ??????????????????
	 * @return
	 */
	public List<Act> todoList(Act act){
		String userId = UserUtils.getUser().getLoginName();//ObjectUtils.toString(UserUtils.getUser().getId());
		
		List<Act> result = new ArrayList<Act>();
		
		// =============== ?????????????????????  ===============
		TaskQuery todoTaskQuery = taskService.createTaskQuery().taskAssignee(userId).active()
				.includeProcessVariables().orderByTaskCreateTime().desc();
		
		// ??????????????????
		if (StringUtils.isNotBlank(act.getProcDefKey())){
			todoTaskQuery.processDefinitionKey(act.getProcDefKey());
		}
		if (act.getBeginDate() != null){
			todoTaskQuery.taskCreatedAfter(act.getBeginDate());
		}
		if (act.getEndDate() != null){
			todoTaskQuery.taskCreatedBefore(act.getEndDate());
		}
		
		// ????????????
		List<Task> todoList = todoTaskQuery.list();
		for (Task task : todoList) {
			Act e = new Act();
			e.setTask(task);
			e.setVars(task.getProcessVariables());
//			e.setTaskVars(task.getTaskLocalVariables());
//			System.out.println(task.getId()+"  =  "+task.getProcessVariables() + "  ========== " + task.getTaskLocalVariables());
			e.setProcDef(ProcessDefCache.get(task.getProcessDefinitionId()));
//			e.setProcIns(runtimeService.createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult());
//			e.setProcExecUrl(ActUtils.getProcExeUrl(task.getProcessDefinitionId()));
			e.setStatus("todo");
			result.add(e);
		}
		
		// =============== ?????????????????????  ===============
		TaskQuery toClaimQuery = taskService.createTaskQuery().taskCandidateUser(userId)
				.includeProcessVariables().active().orderByTaskCreateTime().desc();
		
		// ??????????????????
		if (StringUtils.isNotBlank(act.getProcDefKey())){
			toClaimQuery.processDefinitionKey(act.getProcDefKey());
		}
		if (act.getBeginDate() != null){
			toClaimQuery.taskCreatedAfter(act.getBeginDate());
		}
		if (act.getEndDate() != null){
			toClaimQuery.taskCreatedBefore(act.getEndDate());
		}
		
		// ????????????
		List<Task> toClaimList = toClaimQuery.list();
		for (Task task : toClaimList) {
			Act e = new Act();
			e.setTask(task);
			e.setVars(task.getProcessVariables());
//			e.setTaskVars(task.getTaskLocalVariables());
//			System.out.println(task.getId()+"  =  "+task.getProcessVariables() + "  ========== " + task.getTaskLocalVariables());
			e.setProcDef(ProcessDefCache.get(task.getProcessDefinitionId()));
//			e.setProcIns(runtimeService.createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult());
//			e.setProcExecUrl(ActUtils.getProcExeUrl(task.getProcessDefinitionId()));
			e.setStatus("claim");
			result.add(e);
		}
		return result;
	}
	
	/**
	 * ??????????????????
	 * @param page
	 * @param procDefKey ??????????????????
	 * @return
	 */
	public Page<Act> historicList(Page<Act> page, Act act){
		String userId = UserUtils.getUser().getLoginName();//ObjectUtils.toString(UserUtils.getUser().getId());

		HistoricTaskInstanceQuery histTaskQuery = historyService.createHistoricTaskInstanceQuery().taskAssignee(userId).finished()
				.includeProcessVariables().orderByHistoricTaskInstanceEndTime().desc();
		
		// ??????????????????
		if (StringUtils.isNotBlank(act.getProcDefKey())){
			histTaskQuery.processDefinitionKey(act.getProcDefKey());
		}
		if (act.getBeginDate() != null){
			histTaskQuery.taskCompletedAfter(act.getBeginDate());
		}
		if (act.getEndDate() != null){
			histTaskQuery.taskCompletedBefore(act.getEndDate());
		}
		
		// ????????????
		page.setCount(histTaskQuery.count());
		
		// ????????????
		List<HistoricTaskInstance> histList = histTaskQuery.listPage(page.getFirstResult(), page.getMaxResults());
		//??????????????????
		List<Act> actList=Lists.newArrayList();
		for (HistoricTaskInstance histTask : histList) {
			Act e = new Act();
			e.setHistTask(histTask);
			e.setVars(histTask.getProcessVariables());
//			e.setTaskVars(histTask.getTaskLocalVariables());
//			System.out.println(histTask.getId()+"  =  "+histTask.getProcessVariables() + "  ========== " + histTask.getTaskLocalVariables());
			e.setProcDef(ProcessDefCache.get(histTask.getProcessDefinitionId()));
//			e.setProcIns(runtimeService.createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult());
//			e.setProcExecUrl(ActUtils.getProcExeUrl(task.getProcessDefinitionId()));
			e.setStatus("finish");
			actList.add(e);
			//page.getList().add(e);
		}
		page.setList(actList);
		return page;
	}
	
	/**
	 * ????????????????????????
	 * @param procInsId ????????????
	 * @param startAct ????????????????????????
	 * @param endAct ????????????????????????
	 */
	public List<Act> histoicFlowList(String procInsId, String startAct, String endAct){
		List<Act> actList = Lists.newArrayList();
		List<HistoricActivityInstance> list = historyService.createHistoricActivityInstanceQuery().processInstanceId(procInsId)
				.orderByHistoricActivityInstanceStartTime().asc().orderByHistoricActivityInstanceEndTime().asc().list();
		
		boolean start = false;
		Map<String, Integer> actMap = Maps.newHashMap();
		
		for (int i=0; i<list.size(); i++){
			
			HistoricActivityInstance histIns = list.get(i);
			
			// ??????????????????????????????
			if (StringUtils.isNotBlank(startAct) && startAct.equals(histIns.getActivityId())){
				start = true;
			}
			if (StringUtils.isNotBlank(startAct) && !start){
				continue;
			}
			
			// ????????????????????????????????????????????????????????????????????????
			if (StringUtils.isNotBlank(histIns.getAssignee())
					 || "startEvent".equals(histIns.getActivityType())
					 || "endEvent".equals(histIns.getActivityType())){
				
				// ???????????????????????????
				Integer actNum = actMap.get(histIns.getActivityId());
				if (actNum == null){
					actMap.put(histIns.getActivityId(), actMap.size());
				}
				
				Act e = new Act();
				e.setHistIns(histIns);
				// ???????????????????????????
				if ("startEvent".equals(histIns.getActivityType())){
					List<HistoricProcessInstance> il = historyService.createHistoricProcessInstanceQuery().processInstanceId(procInsId).orderByProcessInstanceStartTime().asc().list();
//					List<HistoricIdentityLink> il = historyService.getHistoricIdentityLinksForProcessInstance(procInsId);
					if (il.size() > 0){
						if (StringUtils.isNotBlank(il.get(0).getStartUserId())){
							User user = UserUtils.getByLoginName(il.get(0).getStartUserId());
							if (user != null){
								e.setAssignee(histIns.getAssignee());
								e.setAssigneeName(user.getName());
							}
						}
					}
				}
				// ???????????????????????????
				if (StringUtils.isNotEmpty(histIns.getAssignee())){
					User user = UserUtils.getByLoginName(histIns.getAssignee());
					if (user != null){
						e.setAssignee(histIns.getAssignee());
						e.setAssigneeName(user.getName());
					}
				}
				// ????????????????????????
				if (StringUtils.isNotBlank(histIns.getTaskId())){
					List<Comment> commentList = taskService.getTaskComments(histIns.getTaskId());
					if (commentList.size()>0){
						e.setComment(commentList.get(0).getFullMessage());
					}
				}
				actList.add(e);
			}
			
			// ??????????????????????????????
			if (StringUtils.isNotBlank(endAct) && endAct.equals(histIns.getActivityId())){
				boolean bl = false;
				Integer actNum = actMap.get(histIns.getActivityId());
				// ??????????????????????????????????????????????????????????????????????????????????????????
				for (int j=i+1; j<list.size(); j++){
					HistoricActivityInstance hi = list.get(j);
					Integer actNumA = actMap.get(hi.getActivityId());
					if ((actNumA != null && actNumA < actNum) || StringUtils.equals(hi.getActivityId(), histIns.getActivityId())){
						bl = true;
					}
				}
				if (!bl){
					break;
				}
			}
		}
		return actList;
	}

	/**
	 * ??????????????????
	 * @param category ????????????
	 */
	public Page<Object[]> processList(Page<Object[]> page, String category) {
		/*
		 * ??????????????????????????????ProcessDefinition??????????????????????????????Deployment??????????????????
		 */
	    ProcessDefinitionQuery processDefinitionQuery = repositoryService.createProcessDefinitionQuery()
	    		.latestVersion().active().orderByProcessDefinitionKey().asc();
	    
	    if (StringUtils.isNotEmpty(category)){
	    	processDefinitionQuery.processDefinitionCategory(category);
		}
	    
	    page.setCount(processDefinitionQuery.count());
	    
	    List<ProcessDefinition> processDefinitionList = processDefinitionQuery.listPage(page.getFirstResult(), page.getMaxResults());
	    for (ProcessDefinition processDefinition : processDefinitionList) {
	      String deploymentId = processDefinition.getDeploymentId();
	      Deployment deployment = repositoryService.createDeploymentQuery().deploymentId(deploymentId).singleResult();
	      page.getList().add(new Object[]{processDefinition, deployment});
	    }
		return page;
	}
	
	/**
	 * ???????????????????????????????????????????????????KEY?????????????????????????????????????????????KEY???
	 * @return
	 */
	public String getFormKey(String procDefId, String taskDefKey){
		String formKey = "";
		if (StringUtils.isNotBlank(procDefId)){
			if (StringUtils.isNotBlank(taskDefKey)){
				try{
					formKey = formService.getTaskFormKey(procDefId, taskDefKey);
				}catch (Exception e) {
					formKey = "";
				}
			}
			if (StringUtils.isBlank(formKey)){
				formKey = formService.getStartFormKey(procDefId);
			}
			if (StringUtils.isBlank(formKey)){
				formKey = "/404";
			}
		}
		logger.debug("getFormKey: {}", formKey);
		return formKey;
	}
	
	/**
	 * ????????????????????????
	 * @param procInsId
	 * @return
	 */
	@Transactional(readOnly = false)
	public ProcessInstance getProcIns(String procInsId) {
		return runtimeService.createProcessInstanceQuery().processInstanceId(procInsId).singleResult();
	}

	/**
	 * ????????????
	 * @param procDefKey ????????????KEY
	 * @param businessTable ???????????????
	 * @param businessId	???????????????
	 * @return ????????????ID
	 */
	@Transactional(readOnly = false)
	public String startProcess(String procDefKey, String businessTable, String businessId) {
		return startProcess(procDefKey, businessTable, businessId, "");
	}
	
	/**
	 * ????????????
	 * @param procDefKey ????????????KEY
	 * @param businessTable ???????????????
	 * @param businessId	???????????????
	 * @param title			??????????????????????????????????????????
	 * @return ????????????ID
	 */
	@Transactional(readOnly = false)
	public String startProcess(String procDefKey, String businessTable, String businessId, String title) {
		Map<String, Object> vars = Maps.newHashMap();
		return startProcess(procDefKey, businessTable, businessId, title, vars);
	}
	
	/**
	 * ????????????
	 * @param procDefKey ????????????KEY
	 * @param businessTable ???????????????
	 * @param businessId	???????????????
	 * @param title			??????????????????????????????????????????
	 * @param vars			????????????
	 * @return ????????????ID
	 */
	@Transactional(readOnly = false)
	public String startProcess(String procDefKey, String businessTable, String businessId, String title, Map<String, Object> vars) {
		String userId = UserUtils.getUser().getLoginName();//ObjectUtils.toString(UserUtils.getUser().getId())
		
		// ?????????????????????????????????ID???????????????????????????ID?????????activiti:initiator???
		identityService.setAuthenticatedUserId(userId);
		
		// ??????????????????
		if (vars == null){
			vars = Maps.newHashMap();
		}
		
		// ??????????????????
		if (StringUtils.isNotBlank(title)){
			vars.put("title", title);
		}
		
		// ????????????
		ProcessInstance procIns = runtimeService.startProcessInstanceByKey(procDefKey, businessTable+":"+businessId, vars);
		
		// ???????????????????????????ID
		Act act = new Act();
		act.setBusinessTable(businessTable);// ????????????
		act.setBusinessId(businessId);	// ?????????ID
		act.setProcInsId(procIns.getId());
		actDao.updateProcInsIdByBusinessId(act);
		return act.getProcInsId();
	}

	/**
	 * ????????????
	 * @param taskId ??????ID
	 */
	public Task getTask(String taskId){
		return taskService.createTaskQuery().taskId(taskId).singleResult();
	}
	
	/**
	 * ????????????
	 * @param taskId ??????ID
	 * @param deleteReason ????????????
	 */
	@Transactional(readOnly = false)
	public void deleteTask(String taskId, String deleteReason){
		taskService.deleteTask(taskId, deleteReason);
	}
	
	/**
	 * ????????????
	 * @param taskId ??????ID
	 * @param userId ????????????ID?????????????????????
	 */
	@Transactional(readOnly = false)
	public void claim(String taskId, String userId){
		taskService.claim(taskId, userId);
	}
	
	/**
	 * ????????????, ???????????????
	 * @param taskId ??????ID
	 * @param procInsId ????????????ID????????????????????????????????????????????????
	 * @param comment ???????????????????????????
	 * @param vars ????????????
	 */
	@Transactional(readOnly = false)
	public void complete(String taskId, String procInsId, String comment, Map<String, Object> vars){
		complete(taskId, procInsId, comment, "", vars);
	}
	
	/**
	 * ????????????, ???????????????
	 * @param taskId ??????ID
	 * @param procInsId ????????????ID????????????????????????????????????????????????
	 * @param comment ???????????????????????????
	 * @param title			??????????????????????????????????????????
	 * @param vars ????????????
	 */
	@Transactional(readOnly = false)
	public void complete(String taskId, String procInsId, String comment, String title, Map<String, Object> vars){
		// ????????????
		if (StringUtils.isNotBlank(procInsId) && StringUtils.isNotBlank(comment)){
			taskService.addComment(taskId, procInsId, comment);
		}
		
		// ??????????????????
		if (vars == null){
			vars = Maps.newHashMap();
		}
		
		// ??????????????????
		if (StringUtils.isNotBlank(title)){
			vars.put("title", title);
		}
		
		// ????????????
		taskService.complete(taskId, vars);
	}

	/**
	 * ?????????????????????
	 * @param procInsId
	 */
	@Transactional(readOnly = false)
	public void completeFirstTask(String procInsId){
		completeFirstTask(procInsId, null, null, null);
	}
	
	/**
	 * ?????????????????????
	 * @param procInsId
	 * @param comment
	 * @param title
	 * @param vars
	 */
	@Transactional(readOnly = false)
	public void completeFirstTask(String procInsId, String comment, String title, Map<String, Object> vars){
		String userId = UserUtils.getUser().getLoginName();
		Task task = taskService.createTaskQuery().taskAssignee(userId).processInstanceId(procInsId).active().singleResult();
		if (task != null){
			complete(task.getId(), procInsId, comment, title, vars);
		}
	}

//	/**
//	 * ????????????
//	 * @param taskId ??????ID
//	 * @param userId ????????????
//	 */
//	public void delegateTask(String taskId, String userId){
//		taskService.delegateTask(taskId, userId);
//	}
//	
//	/**
//	 * ????????????????????????
//	 * @param taskId ??????ID
//	 */
//	public void resolveTask(String taskId){
//		taskService.resolveTask(taskId);
//	}
//	
//	/**
//	 * ????????????
//	 * @param taskId
//	 */
//	public void backTask(String taskId){
//		taskService.
//	}
	
	/**
	 * ??????????????????
	 */
	public void addTaskComment(String taskId, String procInsId, String comment){
		taskService.addComment(taskId, procInsId, comment);
	}
	
	//////////////////  ????????????????????????????????????????????????????????? ??????  https://github.com/bluejoe2008/openwebflow  ////////////////////////////////////////////////// 

	/**
	 * ??????????????????
	 */
	public void taskBack(String procInsId, Map<String, Object> variables) {
		taskBack(getCurrentTask(procInsId), variables);
	}

	/**
	 * ???????????????????????????
	 */
	public void taskBack(TaskEntity currentTaskEntity, Map<String, Object> variables) {
		ActivityImpl activity = (ActivityImpl) ProcessDefUtils
				.getActivity(processEngine, currentTaskEntity.getProcessDefinitionId(), currentTaskEntity.getTaskDefinitionKey())
				.getIncomingTransitions().get(0).getSource();
		jumpTask(currentTaskEntity, activity, variables);
	}

	/**
	 * ??????????????????
	 */
	public void taskForward(String procInsId, Map<String, Object> variables) {
		taskForward(getCurrentTask(procInsId), variables);
	}

	/**
	 * ???????????????????????????
	 */
	public void taskForward(TaskEntity currentTaskEntity, Map<String, Object> variables) {
		ActivityImpl activity = (ActivityImpl) ProcessDefUtils
				.getActivity(processEngine, currentTaskEntity.getProcessDefinitionId(), currentTaskEntity.getTaskDefinitionKey())
				.getOutgoingTransitions().get(0).getDestination();

		jumpTask(currentTaskEntity, activity, variables);
	}
	
	/**
	 * ??????????????????????????????????????????????????????
	 */
	public void jumpTask(String procInsId, String targetTaskDefinitionKey, Map<String, Object> variables) {
		jumpTask(getCurrentTask(procInsId), targetTaskDefinitionKey, variables);
	}

	/**
	 * ??????????????????????????????????????????????????????
	 */
	public void jumpTask(String procInsId, String currentTaskId, String targetTaskDefinitionKey, Map<String, Object> variables) {
		jumpTask(getTaskEntity(currentTaskId), targetTaskDefinitionKey, variables);
	}

	/**
	 * ??????????????????????????????????????????????????????
	 * @param currentTaskEntity ??????????????????
	 * @param targetTaskDefinitionKey ????????????????????????????????????????????????????????????
	 * @throws Exception
	 */
	public void jumpTask(TaskEntity currentTaskEntity, String targetTaskDefinitionKey, Map<String, Object> variables) {
		ActivityImpl activity = ProcessDefUtils.getActivity(processEngine, currentTaskEntity.getProcessDefinitionId(),
				targetTaskDefinitionKey);
		jumpTask(currentTaskEntity, activity, variables);
	}

	/**
	 * ??????????????????????????????????????????????????????
	 * @param currentTaskEntity ??????????????????
	 * @param targetActivity ????????????????????????????????????????????????????????????
	 * @throws Exception
	 */
	private void jumpTask(TaskEntity currentTaskEntity, ActivityImpl targetActivity, Map<String, Object> variables) {
		CommandExecutor commandExecutor = ((RuntimeServiceImpl) runtimeService).getCommandExecutor();
		commandExecutor.execute(new JumpTaskCmd(currentTaskEntity, targetActivity, variables));
	}
	
	/**
	 * ?????????
	 */
	@SuppressWarnings("unchecked")
	public ActivityImpl[] insertTasksAfter(String procDefId, String procInsId, String targetTaskDefinitionKey, Map<String, Object> variables, String... assignees) {
		List<String> assigneeList = new ArrayList<String>();
		assigneeList.add(Authentication.getAuthenticatedUserId());
		assigneeList.addAll(CollectionUtils.arrayToList(assignees));
		String[] newAssignees = assigneeList.toArray(new String[0]);
		ProcessDefinitionEntity processDefinition = (ProcessDefinitionEntity)repositoryService.getProcessDefinition(procDefId);
		ActivityImpl prototypeActivity = ProcessDefUtils.getActivity(processEngine, processDefinition.getId(), targetTaskDefinitionKey);
		return cloneAndMakeChain(processDefinition, procInsId, targetTaskDefinitionKey, prototypeActivity.getOutgoingTransitions().get(0).getDestination().getId(), variables, newAssignees);
	}

	/**
	 * ?????????
	 */
	public ActivityImpl[] insertTasksBefore(String procDefId, String procInsId, String targetTaskDefinitionKey, Map<String, Object> variables, String... assignees) {
		ProcessDefinitionEntity procDef = (ProcessDefinitionEntity)repositoryService.getProcessDefinition(procDefId);
		return cloneAndMakeChain(procDef, procInsId, targetTaskDefinitionKey, targetTaskDefinitionKey, variables, assignees);
	}

	/**
	 * ?????????????????????????????????
	 */
	public ActivityImpl splitTask(String procDefId, String procInsId, String targetTaskDefinitionKey, Map<String, Object> variables, String... assignee) {
		return splitTask(procDefId, procInsId, targetTaskDefinitionKey, variables, true, assignee);
	}
	
	/**
	 * ?????????????????????????????????
	 */
	@SuppressWarnings("unchecked")
	public ActivityImpl splitTask(String procDefId, String procInsId, String targetTaskDefinitionKey, Map<String, Object> variables, boolean isSequential, String... assignees) {
		SimpleRuntimeActivityDefinitionEntity info = new SimpleRuntimeActivityDefinitionEntity();
		info.setProcessDefinitionId(procDefId);
		info.setProcessInstanceId(procInsId);

		RuntimeActivityDefinitionEntityIntepreter radei = new RuntimeActivityDefinitionEntityIntepreter(info);

		radei.setPrototypeActivityId(targetTaskDefinitionKey);
		radei.setAssignees(CollectionUtils.arrayToList(assignees));
		radei.setSequential(isSequential);
		
		ProcessDefinitionEntity processDefinition = (ProcessDefinitionEntity)repositoryService.getProcessDefinition(procDefId);
		ActivityImpl clone = new MultiInstanceActivityCreator().createActivities(processEngine, processDefinition, info)[0];

		TaskEntity currentTaskEntity = this.getCurrentTask(procInsId);
		
		CommandExecutor commandExecutor = ((RuntimeServiceImpl) runtimeService).getCommandExecutor();
		commandExecutor.execute(new CreateAndTakeTransitionCmd(currentTaskEntity, clone, variables));

//		recordActivitiesCreation(info);
		return clone;
	}

	private TaskEntity getCurrentTask(String procInsId) {
		return (TaskEntity) taskService.createTaskQuery().processInstanceId(procInsId).active().singleResult();
	}

	private TaskEntity getTaskEntity(String taskId) {
		return (TaskEntity) taskService.createTaskQuery().taskId(taskId).singleResult();
	}

	@SuppressWarnings("unchecked")
	private ActivityImpl[] cloneAndMakeChain(ProcessDefinitionEntity procDef, String procInsId, String prototypeActivityId, String nextActivityId, Map<String, Object> variables, String... assignees) {
		SimpleRuntimeActivityDefinitionEntity info = new SimpleRuntimeActivityDefinitionEntity();
		info.setProcessDefinitionId(procDef.getId());
		info.setProcessInstanceId(procInsId);

		RuntimeActivityDefinitionEntityIntepreter radei = new RuntimeActivityDefinitionEntityIntepreter(info);
		radei.setPrototypeActivityId(prototypeActivityId);
		radei.setAssignees(CollectionUtils.arrayToList(assignees));
		radei.setNextActivityId(nextActivityId);

		ActivityImpl[] activities = new ChainedActivitiesCreator().createActivities(processEngine, procDef, info);

		jumpTask(procInsId, activities[0].getId(), variables);
//		recordActivitiesCreation(info);

		return activities;
	}
	
//	private void recordActivitiesCreation(SimpleRuntimeActivityDefinitionEntity info) {
//		info.serializeProperties();
//		_activitiesCreationStore.save(info);
//	}
	
	//////////////////////////////////////////////////////////////////// 
	

//	private void recordActivitiesCreation(SimpleRuntimeActivityDefinitionEntity info) throws Exception {
//		info.serializeProperties();
//		_activitiesCreationStore.save(info);
//	}
//
//	/**
//	 * ?????????????????????????????????
//	 * 
//	 * @param targetTaskDefinitionKey
//	 * @param assignee
//	 * @throws IOException
//	 * @throws IllegalAccessException
//	 * @throws IllegalArgumentException
//	 */
//	public ActivityImpl split(String targetTaskDefinitionKey, boolean isSequential, String... assignees) throws Exception {
//		SimpleRuntimeActivityDefinitionEntity info = new SimpleRuntimeActivityDefinitionEntity();
//		info.setProcessDefinitionId(processDefinition.getId());
//		info.setProcessInstanceId(_processInstanceId);
//
//		RuntimeActivityDefinitionEntityIntepreter radei = new RuntimeActivityDefinitionEntityIntepreter(info);
//
//		radei.setPrototypeActivityId(targetTaskDefinitionKey);
//		radei.setAssignees(CollectionUtils.arrayToList(assignees));
//		radei.setSequential(isSequential);
//
//		ActivityImpl clone = new MultiInstanceActivityCreator().createActivities(_processEngine, processDefinition, info)[0];
//
//		TaskEntity currentTaskEntity = getCurrentTask();
//		executeCommand(new CreateAndTakeTransitionCmd(currentTaskEntity.getExecutionId(), clone));
//		executeCommand(new DeleteRunningTaskCmd(currentTaskEntity));
//
//		recordActivitiesCreation(info);
//		return clone;
//	}
//
//	public ActivityImpl split(String targetTaskDefinitionKey, String... assignee) throws Exception {
//		return split(targetTaskDefinitionKey, true, assignee);
//	}

	////////////////////////////////////////////////////////////////////
	
	/**
	 * ????????????????????????
	 * @param executionId	??????ID
	 * @return	???????????????????????????
	 */
	public InputStream tracePhoto(String processDefinitionId, String executionId) {
//		ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(executionId).singleResult();
		BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
		
		List<String> activeActivityIds = Lists.newArrayList();
		if (runtimeService.createExecutionQuery().executionId(executionId).count() > 0){
			activeActivityIds = runtimeService.getActiveActivityIds(executionId);
		}
		
		// ?????????spring??????????????????????????????
		// ProcessEngineImpl defaultProcessEngine = (ProcessEngineImpl)ProcessEngines.getDefaultProcessEngine();
		// Context.setProcessEngineConfiguration(defaultProcessEngine.getProcessEngineConfiguration());

		// ??????spring??????????????????????????????????????????
		Context.setProcessEngineConfiguration(processEngineFactory.getProcessEngineConfiguration());
//		return ProcessDiagramGenerator.generateDiagram(bpmnModel, "png", activeActivityIds);
		return processEngine.getProcessEngineConfiguration().getProcessDiagramGenerator()
				.generateDiagram(bpmnModel, "png", activeActivityIds);
	}
	
	/**
	 * ?????????????????????
	 * @param processInstanceId		????????????ID
	 * @return	???????????????????????????
	 */
	public List<Map<String, Object>> traceProcess(String processInstanceId) throws Exception {
		Execution execution = runtimeService.createExecutionQuery().executionId(processInstanceId).singleResult();//????????????
		Object property = PropertyUtils.getProperty(execution, "activityId");
		String activityId = "";
		if (property != null) {
			activityId = property.toString();
		}
		ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId)
				.singleResult();
		ProcessDefinitionEntity processDefinition = (ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService)
				.getDeployedProcessDefinition(processInstance.getProcessDefinitionId());
		List<ActivityImpl> activitiList = processDefinition.getActivities();//?????????????????????????????????

		List<Map<String, Object>> activityInfos = new ArrayList<Map<String, Object>>();
		for (ActivityImpl activity : activitiList) {

			boolean currentActiviti = false;
			String id = activity.getId();

			// ????????????
			if (id.equals(activityId)) {
				currentActiviti = true;
			}

			Map<String, Object> activityImageInfo = packageSingleActivitiInfo(activity, processInstance, currentActiviti);

			activityInfos.add(activityImageInfo);
		}

		return activityInfos;
	}
	

	/**
	 * ?????????????????????????????????????????????X???Y???????????????????????????????????????????????????
	 * @param activity
	 * @param processInstance
	 * @param currentActiviti
	 * @return
	 */
	private Map<String, Object> packageSingleActivitiInfo(ActivityImpl activity, ProcessInstance processInstance,
			boolean currentActiviti) throws Exception {
		Map<String, Object> vars = new HashMap<String, Object>();
		Map<String, Object> activityInfo = new HashMap<String, Object>();
		activityInfo.put("currentActiviti", currentActiviti);
		setPosition(activity, activityInfo);
		setWidthAndHeight(activity, activityInfo);

		Map<String, Object> properties = activity.getProperties();
		vars.put("????????????", properties.get("name"));
		vars.put("????????????", ActUtils.parseToZhType(properties.get("type").toString()));

		ActivityBehavior activityBehavior = activity.getActivityBehavior();
		logger.debug("activityBehavior={}", activityBehavior);
		if (activityBehavior instanceof UserTaskActivityBehavior) {

			Task currentTask = null;

			// ???????????????task
			if (currentActiviti) {
				currentTask = getCurrentTaskInfo(processInstance);
			}

			// ???????????????????????????
			UserTaskActivityBehavior userTaskActivityBehavior = (UserTaskActivityBehavior) activityBehavior;
			TaskDefinition taskDefinition = userTaskActivityBehavior.getTaskDefinition();
			Set<Expression> candidateGroupIdExpressions = taskDefinition.getCandidateGroupIdExpressions();
			if (!candidateGroupIdExpressions.isEmpty()) {

				// ?????????????????????
				setTaskGroup(vars, candidateGroupIdExpressions);

				// ???????????????
				if (currentTask != null) {
					setCurrentTaskAssignee(vars, currentTask);
				}
			}
		}

		vars.put("????????????", properties.get("documentation"));

		String description = activity.getProcessDefinition().getDescription();
		vars.put("??????", description);

		logger.debug("trace variables: {}", vars);
		activityInfo.put("vars", vars);
		return activityInfo;
	}

	/**
	 * ???????????????
	 * @param vars
	 * @param candidateGroupIdExpressions
	 */
	private void setTaskGroup(Map<String, Object> vars, Set<Expression> candidateGroupIdExpressions) {
		String roles = "";
		for (Expression expression : candidateGroupIdExpressions) {
			String expressionText = expression.getExpressionText();
			String roleName = identityService.createGroupQuery().groupId(expressionText).singleResult().getName();
			roles += roleName;
		}
		vars.put("??????????????????", roles);
	}

	/**
	 * ???????????????????????????
	 * @param vars
	 * @param currentTask
	 */
	private void setCurrentTaskAssignee(Map<String, Object> vars, Task currentTask) {
		String assignee = currentTask.getAssignee();
		if (assignee != null) {
			org.activiti.engine.identity.User assigneeUser = identityService.createUserQuery().userId(assignee).singleResult();
			String userInfo = assigneeUser.getFirstName() + " " + assigneeUser.getLastName();
			vars.put("???????????????", userInfo);
		}
	}

	/**
	 * ????????????????????????
	 * @param processInstance
	 * @return
	 */
	private Task getCurrentTaskInfo(ProcessInstance processInstance) {
		Task currentTask = null;
		try {
			String activitiId = (String) PropertyUtils.getProperty(processInstance, "activityId");
			logger.debug("current activity id: {}", activitiId);

			currentTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).taskDefinitionKey(activitiId)
					.singleResult();
			logger.debug("current task for processInstance: {}", ToStringBuilder.reflectionToString(currentTask));

		} catch (Exception e) {
			logger.error("can not get property activityId from processInstance: {}", processInstance);
		}
		return currentTask;
	}

	/**
	 * ???????????????????????????
	 * @param activity
	 * @param activityInfo
	 */
	private void setWidthAndHeight(ActivityImpl activity, Map<String, Object> activityInfo) {
		activityInfo.put("width", activity.getWidth());
		activityInfo.put("height", activity.getHeight());
	}

	/**
	 * ??????????????????
	 * @param activity
	 * @param activityInfo
	 */
	private void setPosition(ActivityImpl activity, Map<String, Object> activityInfo) {
		activityInfo.put("x", activity.getX());
		activityInfo.put("y", activity.getY());
	}

	public ProcessEngine getProcessEngine() {
		return processEngine;
	}
	
}
