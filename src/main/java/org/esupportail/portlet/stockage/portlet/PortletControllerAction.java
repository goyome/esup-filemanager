/**
 * Copyright (C) 2011 Esup Portail http://www.esup-portail.org
 * Copyright (C) 2011 UNR RUNN http://www.unr-runn.fr
 * @Author (C) 2011 Vincent Bonamy <Vincent.Bonamy@univ-rouen.fr>
 * @Contributor (C) 2011 Jean-Pierre Tran <Jean-Pierre.Tran@univ-rouen.fr>
 * @Contributor (C) 2011 Julien Marchal <Julien.Marchal@univ-nancy2.fr>
 * @Contributor (C) 2011 Julien Gribonvald <Julien.Gribonvald@recia.fr>
 * @Contributor (C) 2011 David Clarke <david.clarke@anu.edu.au>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.esupportail.portlet.stockage.portlet;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletRequest;
import javax.portlet.PortletSession;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

import org.apache.log4j.Logger;
import org.esupportail.portlet.stockage.beans.BasketSession;
import org.esupportail.portlet.stockage.beans.FileUpload;
import org.esupportail.portlet.stockage.beans.FormCommand;
import org.esupportail.portlet.stockage.beans.JsTreeFile;
import org.esupportail.portlet.stockage.beans.SharedUserPortletParameters;
import org.esupportail.portlet.stockage.beans.UserPassword;
import org.esupportail.portlet.stockage.services.ServersAccessService;
import org.esupportail.portlet.stockage.utils.PathEncodingUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.portlet.ModelAndView;
import org.springframework.web.portlet.context.PortletRequestAttributes;

@Controller
@Scope("request")
public class PortletControllerAction  implements InitializingBean {

	protected Logger log = Logger.getLogger(PortletControllerAction.class);
	
	@Autowired
	protected ServersAccessService serverAccess;
	
	@Autowired
	protected BasketSession basketSession;
	
	protected SharedUserPortletParameters userParameters;
		
	
	public void afterPropertiesSet() throws Exception {		
		PortletRequestAttributes requestAttributes = (PortletRequestAttributes)RequestContextHolder.currentRequestAttributes();
		PortletRequest request = requestAttributes.getRequest();
		PortletSession session = request.getPortletSession();

		String sharedSessionId = (String)request.getParameter("sharedSessionId");
		userParameters = (SharedUserPortletParameters) session.getAttribute(sharedSessionId, PortletSession.APPLICATION_SCOPE);
	}
	
	@RequestMapping(value = { "VIEW" }, params = { "action=formProcessWai" })
	public void formProcessWai(FormCommand command, @RequestParam String dir, @RequestParam String sharedSessionId,
			@RequestParam(required = false) String prepareCopy,
			@RequestParam(required = false) String prepareCut,
			@RequestParam(required = false) String past,
			@RequestParam(required = false) String rename,
			@RequestParam(required = false) String delete,
			@RequestParam(required = false) String zip,
			@RequestParam(required = false) String createFolder,
			ActionRequest request, ActionResponse response) throws IOException {
		
		dir = decodeDir(dir);
		
		String msg = null;

		if (zip != null) {
			String url = "/esup-portlet-stockage/servlet-ajax/downloadZip?";
			for(String commandDir: PathEncodingUtils.decodeDirs(command.getDirs())) {
				url = url + "dirs=" + URLEncoder.encode(encodeDir(commandDir), "utf8") + "&";
				url = url + "sharedSessionId=" + URLEncoder.encode(sharedSessionId, "utf8") + "&";
			}
			url = url.substring(0, url.length()-1);
			response.sendRedirect(url);
			
		} else  if (rename != null) {
			response.setRenderParameter("dir", encodeDir(dir));
			response.setRenderParameter("sharedSessionId", sharedSessionId);
			response.setRenderParameter("dirs", encodeDirs(PathEncodingUtils.decodeDirs(command.getDirs())).toArray(new String[] {}));
			response.setRenderParameter("action", "renameWai");
		} else {

			if (prepareCopy != null) {
				basketSession.setDirsToCopy(PathEncodingUtils.decodeDirs(command.getDirs()));
				basketSession.setGoal("copy");
				msg = "ajax.copy.ok";
			} else if (prepareCut != null) {
				basketSession.setDirsToCopy(PathEncodingUtils.decodeDirs(command.getDirs()));
				basketSession.setGoal("cut");
				msg = "ajax.cut.ok";
			} else if (past != null) {
				this.serverAccess.moveCopyFilesIntoDirectory(dir, basketSession
						.getDirsToCopy(), "copy"
						.equals(basketSession.getGoal()), userParameters);
				msg = "ajax.paste.ok";
			} else if (delete != null) {
				msg = "ajax.remove.ok"; 
				for(String dirToDelete: PathEncodingUtils.decodeDirs(command.getDirs())) {
					if(!this.serverAccess.remove(dirToDelete, userParameters)) {
						msg = "ajax.remove.failed"; 
					}
				}
			} 

			if(msg != null)
				response.setRenderParameter("msg", msg);
			response.setRenderParameter("dir", encodeDir(dir));
			response.setRenderParameter("sharedSessionId", sharedSessionId);
			response.setRenderParameter("action", "browseWai");
		}
	}
	
	@RequestMapping(value = {"VIEW"}, params = {"action=createFolderWai"})
    public ModelAndView createFolderWai(RenderRequest request, RenderResponse response,
    								@RequestParam String dir) {
		
		ModelMap model = new ModelMap();	
		model.put("currentDir", dir);
		return new ModelAndView("view-portlet-create-wai", model);
	}
				
	@RequestMapping(value = { "VIEW" }, params = { "action=formCreateWai" })
	public void formCreateWai(FormCommand command, @RequestParam String dir, @RequestParam String sharedSessionId,
			@RequestParam String folderName,
			ActionRequest request, ActionResponse response) throws IOException {
		
		dir = decodeDir(dir);
		
		String msg = null;
		this.serverAccess.createFile(dir, folderName, "folder", userParameters);
		
		if(msg != null)
			response.setRenderParameter("msg", msg);
		response.setRenderParameter("dir", encodeDir(dir));
		response.setRenderParameter("sharedSessionId", sharedSessionId);
		response.setRenderParameter("action", "browseWai");
	}
	
	@RequestMapping(value = {"VIEW"}, params = {"action=renameWai"})
    public ModelAndView renameWai(RenderRequest request, RenderResponse response,
    								@RequestParam String dir,
    								@RequestParam List<String> dirs) {
		
		dir = decodeDir(dir);
		dirs = decodeDirs(dirs);
		
		ModelMap model = new ModelMap();
		List<JsTreeFile> files = this.serverAccess.getChildren(dir, userParameters);
		List<JsTreeFile> filesToRename = new ArrayList<JsTreeFile>();
		if(!dirs.isEmpty()) {
			for(JsTreeFile file: files) {
				if(dirs.contains(file.getPath()))
					filesToRename.add(file);
			}	
		} else {
			filesToRename = files;
		}
		model.put("files", filesToRename);
		PathEncodingUtils.encodeDir(files);
		model.put("currentDir", encodeDir(dir));
		return new ModelAndView("view-portlet-rename-wai", model);
	}
	
	@RequestMapping(value = { "VIEW" }, params = { "action=formRenameWai" })
	public void formRenameWai(@RequestParam String dir, @RequestParam String sharedSessionId,
			ActionRequest request, ActionResponse response) throws IOException {
		
		dir = decodeDir(dir);

		List<JsTreeFile> files = this.serverAccess.getChildren(dir, userParameters);
		for(JsTreeFile file: files) {
			String newTitle = request.getParameter(file.getEncPath());
			if(newTitle != null && newTitle.length() != 0 && !file.getTitle().equals(newTitle)) {
				this.serverAccess.renameFile(file.getPath(), newTitle, userParameters);
			}
		}
		
		response.setRenderParameter("dir", encodeDir(dir));
		response.setRenderParameter("sharedSessionId", sharedSessionId);
		response.setRenderParameter("action", "browseWai");
	}
	
	@RequestMapping(value = {"VIEW"}, params = {"action=fileUploadWai"})
    public ModelAndView fileUploadWai(RenderRequest request, RenderResponse response,
    								@RequestParam String dir) {
		
		ModelMap model = new ModelMap();
		model.put("currentDir", dir);
		return new ModelAndView("view-portlet-upload-wai", model);
	}
	
	
	@RequestMapping(value = {"VIEW"}, params = {"action=formUploadWai"})
    public void formUploadWai(ActionRequest request, ActionResponse response,
    								@RequestParam String dir, @RequestParam String sharedSessionId, FileUpload command) throws IOException {
		
		dir = decodeDir(dir);
		
		String filename = command.getQqfile().getOriginalFilename();
		InputStream inputStream = command.getQqfile().getInputStream();
		this.serverAccess.putFile(dir, filename, inputStream, userParameters);
		
		response.setRenderParameter("dir", encodeDir(dir));
		response.setRenderParameter("sharedSessionId", sharedSessionId);
		response.setRenderParameter("action", "browseWai");
	}
	
	@RequestMapping(value = {"VIEW"}, params = {"action=formAuthenticationWai"})
    public void formAuthenticationWai(ActionRequest request, ActionResponse response,
    								@RequestParam String dir, @RequestParam String sharedSessionId, @RequestParam String username, @RequestParam String password) throws IOException {
		
		dir = decodeDir(dir);
		
		String msg = "auth.bad";
		if(this.serverAccess.authenticate(dir, username, password, userParameters)) {
			msg = "auth.ok";
		
			// we keep username+password in session so that we can reauthenticate on drive in servlet mode 
			// (and so that download file would be ok for example with the servlet ...)
			String driveName = this.serverAccess.getDrive(dir);
			userParameters.getUserPassword4AuthenticatedFormDrives().put(driveName, new UserPassword(username, password));
		}
			
		response.setRenderParameter("msg", msg);
		response.setRenderParameter("dir", encodeDir(dir));
		response.setRenderParameter("sharedSessionId", sharedSessionId);
		response.setRenderParameter("action", "browseWai");
	}
    
	@RequestMapping(value = {"VIEW"}, params = {"action=formAuthenticationMobile"})
    public void formAuthenticationMobile(ActionRequest request, ActionResponse response,
    								@RequestParam String dir, @RequestParam String sharedSessionId, @RequestParam String username, @RequestParam String password) throws IOException {
		
		dir = decodeDir(dir);
		
		String msg = "auth.bad";
		if(this.serverAccess.authenticate(dir, username, password, userParameters)) {
			msg = "auth.ok";
			
			// we keep username+password in session so that we can reauthenticate on drive in servlet mode 
			// (and so that download file would be ok for example with the servlet ...)
			String driveName = this.serverAccess.getDrive(dir);
			userParameters.getUserPassword4AuthenticatedFormDrives().put(driveName, new UserPassword(username, password));
		}
		
		response.setRenderParameter("msg", msg);
		response.setRenderParameter("dir", encodeDir(dir));
		response.setRenderParameter("sharedSessionId", sharedSessionId);
		response.setRenderParameter("action", "browseMobile");
	}

    private String decodeDir(String dir) {
        return PathEncodingUtils.decodeDir(dir);
    }
    
    private List<String> decodeDirs(List<String> dirs) {
        return PathEncodingUtils.decodeDirs(dirs);
    }
    
    private String encodeDir(String dir) {
        return PathEncodingUtils.encodeDir(dir);
    }
    
    private List<String> encodeDirs(List<String> dirs) {
        return PathEncodingUtils.encodeDirs(dirs);
    }
    
}
