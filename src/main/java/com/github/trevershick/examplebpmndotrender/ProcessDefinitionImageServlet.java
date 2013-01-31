package com.github.trevershick.examplebpmndotrender;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.impl.util.json.JSONObject;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.apache.commons.io.IOUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 * Serves up PNG images from the Activiti Repository Service.
 * URLs:
 * (example urls)
 * 
 * {servlet mapping}"/(p[id])/([ik])/([^.]+).(png|json)";
 * {servlet mapping}/pi/10435/image.png
 * {servlet mapping}/pd/TestProcess/TestProcess.png
 * 
 */
public class ProcessDefinitionImageServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private AtomicReference<RepositoryService> repositoryService = new AtomicReference<RepositoryService>();
	private AtomicReference<RuntimeService> runtimeService = new AtomicReference<RuntimeService>();
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public ProcessDefinitionImageServlet() {
        super();
    }

    enum QueryType {
    	/* by process instance */
    	pi,
    	/* by process definition */
    	pd;
    }
    enum KeyType {
    	/* process instance id */
    	i,
    	/* process key */
    	k
    }
    enum ContentType {
    	png,json
    }
	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String pathInfo = request.getPathInfo();
		// path info is the process instance id
		
		String regex = "/(p[id])/([ik])/([^.]+).(png|json)";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(pathInfo);
		if (!matcher.matches()) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, String.format("Expected %s", request.getServletPath() + regex));
			return;
		}
		
		QueryType queryType = QueryType.valueOf(matcher.group(1));
		KeyType keyType = KeyType.valueOf(matcher.group(2));
		String idOrKeyValue = matcher.group(3);
		ContentType contentType = ContentType.valueOf(matcher.group(4));
		switch (contentType) {
		case json:
			handleJson(request, response, queryType, keyType, idOrKeyValue);
		case png:
			handlePng(response, queryType, keyType, idOrKeyValue);		
		}

	}

	private void handlePng(HttpServletResponse response, QueryType queryType,
			KeyType keyType, String idOrKeyValue) throws IOException {
		InputStream result = null;
		ServletOutputStream os = null;
		try {
			result = imageInputStream(queryType, keyType, idOrKeyValue);
		} catch (ProcessDefinitionNotFoundException e) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND,"Process Definition Not Found");
			return;
		} catch (ProcessInstanceNotFoundException e) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND,"Process Instance Not Found");
			return;
		} catch (NoDiagramException e) {
			result = defaultImage();
		} 
		try {
			response.setContentType("image/png");
			os = response.getOutputStream();
			IOUtils.copy(result, os);
		} finally {
			IOUtils.closeQuietly(os);
			IOUtils.closeQuietly(result);
		}
	}

	private void handleJson(HttpServletRequest request,
			HttpServletResponse response, QueryType queryType, KeyType keyType,
			String idOrKeyValue) throws IOException {
		ProcessDefinition pd;
		ServletOutputStream os = response.getOutputStream();
		JSONObject obj = new JSONObject();
		try {
			pd = pd(queryType,keyType,idOrKeyValue);
			String link = request.getServletPath() + "/pd/i/" + pd.getId() + ".png";
			obj.put("link", link);
		} catch (ProcessDefinitionNotFoundException e) {
			obj.put("error","no process definition found");
		} catch (ProcessInstanceNotFoundException e) {
			obj.put("error","no process instance found");
		} catch (NoDiagramException e) {
			obj.put("error","no diagram available");
		}
		try {
			response.setContentType("application/json");
			os = response.getOutputStream();
			IOUtils.write(obj.toString(), os);
		} finally {
			IOUtils.closeQuietly(os);
		}
	}	

	private InputStream defaultImage() {
		return getClass().getResourceAsStream("/com/railinc/loa/no_image_available.png");
	}
	
	private InputStream imageInputStream(QueryType queryType, KeyType keyType,
			String idOrKeyValue) throws ProcessDefinitionNotFoundException, ProcessInstanceNotFoundException, NoDiagramException {
		ProcessDefinition pd = pd(queryType,keyType,idOrKeyValue);
		String diagramResourceName = pd.getDiagramResourceName();
	
		if (diagramResourceName == null) {
			throw new NoDiagramException();
		}
		InputStream is = repo().getResourceAsStream(pd.getDeploymentId(), diagramResourceName);
		if (is == null) { throw new NoDiagramException(); }
		return is;
	}
	/**
	 * 
	 * @param queryType
	 * @param keyType
	 * @param idOrKeyValue
	 * @return diagram resource name
	 * @throws ProcessDefinitionNotFoundException
	 * @throws ProcessInstanceNotFoundException
	 * @throws NoDiagramException
	 */
	private ProcessDefinition pd(QueryType queryType, KeyType keyType,
			String idOrKeyValue) throws ProcessDefinitionNotFoundException, ProcessInstanceNotFoundException, NoDiagramException {

		String pdid = processDefinitionIdFrom(queryType, keyType, idOrKeyValue);
		RepositoryService repo = repo();
		ProcessDefinition processDefinition = repo
			.createProcessDefinitionQuery()
			.processDefinitionId(pdid)
			.singleResult();
		if (processDefinition == null) {
			throw new ProcessDefinitionNotFoundException();
		}

		return processDefinition;
		
	}

	private String processDefinitionIdFrom(QueryType queryType, KeyType keyType,
			String idOrKeyValue) throws ProcessInstanceNotFoundException, ProcessDefinitionNotFoundException {
		switch (queryType) {
		case pd:
			return processDefinitionIdFromProcessDefinition(keyType, idOrKeyValue);
		case pi:
			return processDefinitionIdFromProcessInstance(keyType, idOrKeyValue);
		default:
			throw new IllegalArgumentException();

		}
	}


	private String processDefinitionIdFromProcessInstance(KeyType keyType,
			String idOrKeyValue) throws ProcessInstanceNotFoundException {
		ProcessInstance instance = runtimeService().createProcessInstanceQuery().processInstanceId(idOrKeyValue).singleResult();
		if (instance == null) {
			throw new ProcessInstanceNotFoundException();
		}
		return instance.getProcessDefinitionId();
	}

	private String processDefinitionIdFromProcessDefinition(KeyType keyType,
			String idOrKeyValue) throws ProcessDefinitionNotFoundException {
		ProcessDefinition singleResult = null;
		switch (keyType) {
		case i:
			return idOrKeyValue;
		case k:
			singleResult = repo().createProcessDefinitionQuery()
				.processDefinitionKey(idOrKeyValue)
				.latestVersion()
				.singleResult();
			if (singleResult == null) {
				throw new ProcessDefinitionNotFoundException();
			}
			return singleResult.getId();
		default:
			throw new IllegalArgumentException();
		}
	}

	private RuntimeService runtimeService() {
		RuntimeService s = runtimeService.get();
		WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(getServletContext());
		if (s == null) {
			s = ctx.getBean(RuntimeService.class);
			runtimeService.set(s); 
		}
		return s;
	}

	private RepositoryService repo() {
		RepositoryService s = repositoryService.get();
		WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(getServletContext());
		if (s == null) {
			s = ctx.getBean(RepositoryService.class);
			repositoryService.set(s); 
		}
		return s;
	}
	
	
    private final class NoDiagramException extends Exception{
		private static final long serialVersionUID = -5208270121842478449L;
	}
    private final class ProcessDefinitionNotFoundException extends Exception{
		private static final long serialVersionUID = -1130671839062602971L;
	}
    private final class ProcessInstanceNotFoundException extends Exception{
		private static final long serialVersionUID = 9096009530542305828L;
	}

}
