/*
 * Knowage, Open Source Business Intelligence suite
 * Copyright (C) 2016 Engineering Ingegneria Informatica S.p.A.
 *
 * Knowage is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knowage is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.eng.spagobi.api;

import it.eng.spago.error.EMFInternalError;
import it.eng.spago.error.EMFUserError;
import it.eng.spagobi.analiticalmodel.document.AnalyticalModelDocumentManagementAPI;
import it.eng.spagobi.analiticalmodel.document.DocumentExecutionUtils;
import it.eng.spagobi.analiticalmodel.document.bo.BIObject;
import it.eng.spagobi.analiticalmodel.document.bo.SubObject;
import it.eng.spagobi.analiticalmodel.document.dao.IBIObjectDAO;
import it.eng.spagobi.analiticalmodel.document.handlers.DocumentParameters;
import it.eng.spagobi.behaviouralmodel.analyticaldriver.bo.BIObjectParameter;
import it.eng.spagobi.behaviouralmodel.analyticaldriver.bo.Parameter;
import it.eng.spagobi.commons.bo.UserProfile;
import it.eng.spagobi.commons.dao.DAOFactory;
import it.eng.spagobi.commons.utilities.indexing.LuceneIndexer;
import it.eng.spagobi.commons.utilities.messages.IMessageBuilder;
import it.eng.spagobi.commons.utilities.messages.MessageBuilder;
import it.eng.spagobi.commons.utilities.messages.MessageBuilderFactory;
import it.eng.spagobi.services.rest.annotations.ManageAuthorization;
import it.eng.spagobi.tools.objmetadata.bo.ObjMetacontent;
import it.eng.spagobi.tools.objmetadata.bo.ObjMetadata;
import it.eng.spagobi.tools.objmetadata.dao.IObjMetacontentDAO;
import it.eng.spagobi.utilities.exceptions.SpagoBIRuntimeException;
import it.eng.spagobi.utilities.rest.RestUtilities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

@Path("/1.0/documentexecution")
@ManageAuthorization
public class DocumentExecutionResource extends AbstractSpagoBIResource {

	// GENERAL METADATA NAMES
	public static final String LABEL = "metadata.docLabel";
	public static final String NAME = "metadata.docName";
	public static final String TYPE = "metadata.docType";
	public static final String ENG_NAME = "metadata.docEngine";
	public static final String RATING = "metadata.docRating";
	public static final String SUBOBJ_NAME = "metadata.subobjName";
	public static final String METADATA = "METADATA";

	// public static final String PARAMETERS = "PARAMETERS";
	// public static final String SERVICE_NAME = "GET_URL_FOR_EXECUTION_ACTION";
	public static String MODE_SIMPLE = "simple";
	// public static String MODE_COMPLETE = "complete";
	// public static String START = "start";
	// public static String LIMIT = "limit";

	private static IMessageBuilder message = MessageBuilderFactory.getMessageBuilder();

	private class DocumentExecutionException extends Exception {
		private static final long serialVersionUID = -1882998632783944575L;

		DocumentExecutionException(String message) {
			super(message);
		}
	}

	static protected Logger logger = Logger.getLogger(DocumentExecutionResource.class);
	protected AnalyticalModelDocumentManagementAPI documentManager = new AnalyticalModelDocumentManagementAPI(getUserProfile());

	/**
	 * @return { executionURL: 'http:...', errors: 1 - 'role missing' 2 -'Missing paramters' [list of missing mandatory filters ] 3 -'operation not allowed' [if
	 *         the request role is not owned by the requesting user] }
	 * @throws EMFInternalError
	 */
	@GET
	@Path("/url")
	@Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
	public Response getDocumentExecutionURL(@QueryParam("label") String label, @QueryParam("role") String role, @QueryParam("modality") String modality,
			@QueryParam("displayToolbar") String displayToolbar, @QueryParam("parameters") String jsonParameters, @QueryParam("snapshotId") String snapshotId,
			@QueryParam("subObjectID") String subObjectID, @Context HttpServletRequest req) {

		logger.debug("IN");
		HashMap<String, Object> resultAsMap = new HashMap<String, Object>();
		List errorList = new ArrayList<>();
		MessageBuilder m = new MessageBuilder();
		Locale locale = m.getLocale(req);
		try {
			String executingRole = getExecutionRole(role);
			// displayToolbar
			// modality
			BIObject obj = DAOFactory.getBIObjectDAO().loadBIObjectForExecutionByLabelAndRole(label, executingRole);
			List<DocumentParameters> parameters = DocumentExecutionUtils.getParameters(obj, executingRole, locale, modality);
			String url = DocumentExecutionUtils.handleNormalExecutionUrl(this.getUserProfile(), obj, req, this.getAttributeAsString("SBI_ENVIRONMENT"),
					executingRole, modality, jsonParameters, locale);
			errorList = DocumentExecutionUtils.handleNormalExecutionError(this.getUserProfile(), obj, req, this.getAttributeAsString("SBI_ENVIRONMENT"),
					executingRole, modality, jsonParameters, locale);
			resultAsMap.put("parameters", parameters);
			resultAsMap.put("url", url);
			resultAsMap.put("errors", errorList);

		} catch (DocumentExecutionException e) {
			errorList.add(e);
			resultAsMap.put("documentError", errorList);
			resultAsMap.put("url", "");
			resultAsMap.put("parameters", "");
		} catch (Exception e) {
			logger.error("Error while getting the document execution url", e);
			throw new SpagoBIRuntimeException("Error while getting the document execution url", e);
		}

		logger.debug("OUT");
		return Response.ok(resultAsMap).build();
	}

	/**
	 * @return { filterStatus: [{ title: 'Provincia', urlName: 'provincia', type: 'list', lista:[[k,v],[k,v], [k,v]] }, { title: 'Comune', urlName: 'comune',
	 *         type: 'list', lista:[], dependsOn: 'provincia' }, { title: 'Free Search', type: 'manual', urlName: 'freesearch' }],
	 * 
	 *         errors: [ 'role missing', 'operation not allowed' ] }
	 * @throws EMFUserError
	 */
	@GET
	@Path("/filters")
	@Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
	public Response getDocumentExecutionFilters(@QueryParam("label") String label, @QueryParam("role") String role,
			@QueryParam("parameters") String jsonParameters, @Context HttpServletRequest req) throws DocumentExecutionException, EMFUserError {

		logger.debug("IN");

		String toBeReturned = "{}";
		HashMap<String, Object> resultAsMap = new HashMap<String, Object>();

		IBIObjectDAO dao = DAOFactory.getBIObjectDAO();
		BIObject biObject = dao.loadBIObjectForExecutionByLabelAndRole(label, role);

		List<BIObjectParameter> parametersList = biObject.getBiObjectParameters();

		ArrayList<HashMap<String, Object>> parametersArrayList = new ArrayList<>();

		for (BIObjectParameter objParameter : parametersList) {
			Parameter parameter = objParameter.getParameter();

			HashMap<String, Object> parameterAsMap = new HashMap<String, Object>();

			parameterAsMap.put("id", objParameter.getId());
			parameterAsMap.put("label", objParameter.getLabel());
			parameterAsMap.put("urlName", objParameter.getParameterUrlName());
			parameterAsMap.put("type", parameter.getType());
			parameterAsMap.put("typeCode", parameter.getModalityValue().getITypeCd());
			parameterAsMap.put("selectionType", parameter.getModalityValue().getSelectionType());
			parameterAsMap.put("valueSelection", parameter.getValueSelection());
			parameterAsMap.put("selectedLayer", parameter.getSelectedLayer());
			parameterAsMap.put("selectedLayerProp", parameter.getSelectedLayerProp());
			parameterAsMap.put("visible", ((objParameter.getVisible() == 1)));
			parameterAsMap.put("mandatory", ((objParameter.getRequired() == 1)));
			parameterAsMap.put("multivalue", objParameter.isMultivalue());
			parameterAsMap.put("dependsOn", new ArrayList<>());

			if (parameter.getValueSelection().equalsIgnoreCase("lov")) {
				ArrayList<HashMap<String, Object>> defaultValues = DocumentExecutionUtils.getLovDefaultValues(role, biObject, objParameter, req);

				parameterAsMap.put("defaultValues", defaultValues);
			}

			parametersArrayList.add(parameterAsMap);
		}

		if (parametersList.size() > 0) {
			resultAsMap.put("filterStatus", parametersArrayList);
		} else {
			resultAsMap.put("filterStatus", new ArrayList<>());
		}
		resultAsMap.put("errors", new ArrayList<>());
		logger.debug("OUT");
		return Response.ok(resultAsMap).build();
	}

	/**
	 * @return the list of values when input parameter (urlName) is correlated to another
	 */
	@GET
	@Path("/filterlist")
	@Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
	public Response getDocumentExecutionFilterList(@QueryParam("label") String label, @QueryParam("role") String role,
			@QueryParam("parameters") String jsonParameters, @QueryParam("urlName") String urlName, @Context HttpServletRequest req) {
		logger.debug("IN");

		String toBeReturned = "{}";

		try {
			role = getExecutionRole(role);

		} catch (DocumentExecutionException e) {
			return Response.ok("{errors: '" + e.getMessage() + "', }").build();
		} catch (Exception e) {
			logger.error("Error while getting the document execution filterlist", e);
			throw new SpagoBIRuntimeException("Error while getting the document execution filterlist", e);
		}

		logger.debug("OUT");
		return Response.ok(toBeReturned).build();
	}

	/**
	 * Produces a json of document metadata grouped by typeCode ("GENERAL_META", "LONG_TEXT", "SHORT_TEXT")
	 * 
	 * @param id
	 *            of document
	 * @param id
	 *            of subObject
	 * @param httpRequest
	 * @return a response with a json
	 * @throws EMFUserError
	 */
	@GET
	@Path("/{id}/documentMetadata")
	public Response documentMetadata(@PathParam("id") Integer objectId, @QueryParam("subobjectId") Integer subObjectId, @Context HttpServletRequest httpRequest)
			throws EMFUserError {

		try {
			MessageBuilder msgBuild = new MessageBuilder();
			Locale locale = msgBuild.getLocale(httpRequest);

			Map<String, JSONArray> documentMetadataMap = new HashMap<>();

			JSONArray generalMetadata = new JSONArray();
			documentMetadataMap.put("GENERAL_META", generalMetadata);

			// START GENERAL METADATA
			if (subObjectId != null) {
				// SubObj Name
				String textSubName = msgBuild.getMessage(SUBOBJ_NAME, locale);
				SubObject subobj = DAOFactory.getSubObjectDAO().getSubObject(subObjectId);
				addMetadata(generalMetadata, textSubName, subobj.getName());
			}

			BIObject obj = DAOFactory.getBIObjectDAO().loadBIObjectById(objectId);

			// Obj Label
			String textLabel = msgBuild.getMessage(LABEL, locale);
			addMetadata(generalMetadata, textLabel, obj.getLabel());

			// Obj Name
			String textName = msgBuild.getMessage(NAME, locale);
			addMetadata(generalMetadata, textName, obj.getName());

			// Obj Type
			String textType = msgBuild.getMessage(TYPE, locale);
			addMetadata(generalMetadata, textType, obj.getBiObjectTypeCode());

			// Obj Engine Name
			String textEngName = msgBuild.getMessage(ENG_NAME, locale);
			addMetadata(generalMetadata, textEngName, obj.getEngine().getName());

			// END GENERAL METADATA

			List metadata = DAOFactory.getObjMetadataDAO().loadAllObjMetadata();
			if (metadata != null && !metadata.isEmpty()) {
				Iterator it = metadata.iterator();
				while (it.hasNext()) {
					ObjMetadata objMetadata = (ObjMetadata) it.next();
					ObjMetacontent objMetacontent = DAOFactory.getObjMetacontentDAO().loadObjMetacontent(objMetadata.getObjMetaId(), objectId, subObjectId);
					addTextMetadata(documentMetadataMap, objMetadata.getDataTypeCode(), objMetadata.getName(),
							objMetacontent != null && objMetacontent.getContent() != null ? new String(objMetacontent.getContent()) : "",
							objMetadata.getObjMetaId());
				}
			}

			if (!documentMetadataMap.isEmpty()) {
				return Response.ok(new JSONObject(documentMetadataMap).toString()).build();
			}
		} catch (Exception e) {
			logger.error(httpRequest.getPathInfo(), e);
		}

		return Response.ok().build();
	}

	@POST
	@Path("/saveDocumentMetadata")
	public Response saveDocumentMetadata(@Context HttpServletRequest httpRequest) throws JSONException {
		try {
			JSONObject params = RestUtilities.readBodyAsJSONObject(httpRequest);
			IObjMetacontentDAO dao = DAOFactory.getObjMetacontentDAO();
			dao.setUserProfile(getUserProfile());
			Integer biobjectId = params.getInt("id");
			Integer subobjectId = params.has("subobjectId") ? params.getInt("subobjectId") : null;
			String jsonMeta = params.getString("jsonMeta");

			logger.debug("Object id = " + biobjectId);
			logger.debug("Subobject id = " + subobjectId);

			JSONArray metadata = new JSONArray(jsonMeta);
			for (int i = 0; i < metadata.length(); i++) {
				JSONObject aMetadata = metadata.getJSONObject(i);
				Integer metadataId = aMetadata.getInt("id");
				String text = aMetadata.getString("value");
				ObjMetacontent aObjMetacontent = dao.loadObjMetacontent(metadataId, biobjectId, subobjectId);
				if (aObjMetacontent == null) {
					logger.debug("ObjMetacontent for metadata id = " + metadataId + ", biobject id = " + biobjectId + ", subobject id = " + subobjectId
							+ " was not found, creating a new one...");
					aObjMetacontent = new ObjMetacontent();
					aObjMetacontent.setObjmetaId(metadataId);
					aObjMetacontent.setBiobjId(biobjectId);
					aObjMetacontent.setSubobjId(subobjectId);
					aObjMetacontent.setContent(text.getBytes("UTF-8"));
					aObjMetacontent.setCreationDate(new Date());
					aObjMetacontent.setLastChangeDate(new Date());
					dao.insertObjMetacontent(aObjMetacontent);
				} else {
					logger.debug("ObjMetacontent for metadata id = " + metadataId + ", biobject id = " + biobjectId + ", subobject id = " + subobjectId
							+ " was found, it will be modified...");
					aObjMetacontent.setContent(text.getBytes("UTF-8"));
					aObjMetacontent.setLastChangeDate(new Date());
					dao.modifyObjMetacontent(aObjMetacontent);
				}

			}
			/*
			 * indexes biobject by modifying document in index
			 */
			BIObject biObjToIndex = DAOFactory.getBIObjectDAO().loadBIObjectById(biobjectId);
			LuceneIndexer.updateBiobjInIndex(biObjToIndex, false);

		} catch (Exception e) {
			logger.error(request.getPathInfo(), e);
			return Response.ok(new JSONObject("{\"errors\":[{\"message\":\"Exception occurred while saving metadata\"}]}").toString()).build();
		}
		return Response.ok().build();
	}

	private void addMetadata(JSONArray generalMetadata, String name, String value) throws JsonMappingException, JsonParseException, JSONException, IOException {
		addMetadata(generalMetadata, name, value, null);
	}

	private void addMetadata(JSONArray generalMetadata, String name, String value, Integer id) throws JsonMappingException, JsonParseException, JSONException,
			IOException {
		JSONObject data = new JSONObject();
		if (id != null) {
			data.put("id", id);
		}
		data.put("name", name);
		data.put("value", value);
		generalMetadata.put(data);
	}

	private void addTextMetadata(Map<String, JSONArray> metadataMap, String type, String name, String value, Integer id) throws JSONException,
			JsonMappingException, JsonParseException, IOException {
		JSONArray jsonArray = metadataMap.get(type);
		if (jsonArray == null) {
			jsonArray = new JSONArray();
		}
		addMetadata(jsonArray, name, value, id);
		metadataMap.put(type, jsonArray);
	}

	protected String getExecutionRole(String role) throws EMFInternalError, DocumentExecutionException {
		UserProfile userProfile = getUserProfile();
		if (role != null && !role.equals("")) {
			logger.debug("role for document execution: " + role);
		} else {
			if (userProfile.getRoles().size() == 1) {
				role = userProfile.getRoles().iterator().next().toString();
				logger.debug("profile role for document execution: " + role);
			} else {
				logger.debug("missing role for document execution, role:" + role);
				throw new DocumentExecutionException(message.getMessage("SBIDev.docConf.execBIObject.selRoles.Title"));
			}
		}

		return role;
	}

}
