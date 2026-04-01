package pt.unl.fct.di.adc.firstwebapp.resources;

import java.util.*;
import java.util.logging.Logger;

import jakarta.ws.rs.core.*;
import org.apache.commons.codec.digest.DigestUtils;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response.Status;

import jakarta.servlet.http.HttpServletRequest;

import pt.unl.fct.di.adc.firstwebapp.util.AuthToken;
import pt.unl.fct.di.adc.firstwebapp.util.LoginData;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.PathElement;
import com.google.cloud.datastore.StringValue;
import com.google.cloud.datastore.Transaction;
import com.google.cloud.datastore.QueryResults;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.StructuredQuery.OrderBy;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import com.google.cloud.datastore.StructuredQuery.CompositeFilter;

import com.google.gson.Gson;
import pt.unl.fct.di.adc.firstwebapp.util.*;


@Path("/")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class LoginResource {

	private static final String USER_PWD = "user_pwd";
	private static final String USER_LOGIN_TIME = "user_login_time";

	/** 
	 * Logger Object
	 */
	private static final Logger LOG = Logger.getLogger(LoginResource.class.getName());
	private static final Datastore datastore = DatastoreOptions.newBuilder()
			.setProjectId("projeto-adc-491613")
			.build()
			.getService();
	private static final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");
	
	public LoginResource() {} // Nothing to be done here
	
	@POST
	@Path("/login")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response doLogin(RequestWrapper<LoginData> request) {
		LoginData data = request.input;
        String username = data.username.trim();
		LOG.info("Login attempt for: " + data.username);

		try {
			Key userKey = datastore.newKeyFactory().setKind("User").newKey(username);
			Entity user = datastore.get(userKey);

			if (user == null) {
				return MessageHelper.error(ErrorMessages.USER_NOT_FOUND, ErrorMessages.USER_NOT_FOUND_MSG);
			}

			String hashedInputPassword = DigestUtils.sha512Hex(data.password);
			if (!user.getString("user_pwd").equals(hashedInputPassword)) {
				return MessageHelper.error(ErrorMessages.INVALID_CREDENTIALS, ErrorMessages.INVALID_CREDENTIALS_MSG);
			}

			AuthToken token = new AuthToken(username);

			Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(token.tokenID);
			Entity tokenEntity = Entity.newBuilder(tokenKey)
					.set("username", token.username)
					.set("creationDate", token.creationDate)
					.set("expirationDate", token.expirationDate)
					.set("user_role", user.getString("user_role"))
					.build();

			datastore.put(tokenEntity);

			LOG.info("Login successful for user: " + username);
			return MessageHelper.success(token);

		} catch (Exception e) {
			LOG.severe("Login error: " + e.getMessage());
			return MessageHelper.error(ErrorMessages.INTERNAL_ERROR, ErrorMessages.INTERNAL_ERROR_MSG);
		}
	}

	@POST
	@Path("/showauthsessions")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response showAuthSessions(RequestWrapper<Void> request) {
		if(request == null || request.token == null || request.token.tokenID == null) {
			return MessageHelper.error(ErrorMessages.INVALID_INPUT, ErrorMessages.INVALID_INPUT_MSG);
		}

		try {
			Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(request.token.tokenID);
			Entity tokenEntity = datastore.get(tokenKey);

			Response error;
			if ((error = AuthUtils.validateToken(tokenEntity)) != null) return error;

			String callerRole = tokenEntity.getString("user_role");
			if (AuthUtils.getRoleWeight(callerRole) < 3) {
				return MessageHelper.error(ErrorMessages.UNAUTHORIZED, ErrorMessages.UNAUTHORIZED_MSG);
			}

			long currentTime = System.currentTimeMillis();
			Query<Entity> query = Query.newEntityQueryBuilder()
					.setKind("Token")
					.setFilter(PropertyFilter.gt("expirationDate", currentTime))
					.build();
			QueryResults<Entity> results = datastore.run(query);

			List<Map<String, Object>> sessionsList = new ArrayList<>();

			while (results.hasNext()) {
				Entity token = results.next();

                String username = token.getString("username");
                Key userKey = datastore.newKeyFactory().setKind("User").newKey(username);
                Entity userEntity = datastore.get(userKey);

				Map<String, Object> s = new LinkedHashMap<>();
				s.put("tokenID", token.getKey().getName());
				s.put("username", token.getString("username"));
				s.put("role", userEntity.getString("user_role"));
				s.put("expiresAt", token.getLong("expirationDate"));

				sessionsList.add(s);
			}

			Map<String, Object> dataField = new LinkedHashMap<>();
			dataField.put("sessions", sessionsList);

			return MessageHelper.success(dataField);

		} catch (Exception e) {
			LOG.severe("Error in showSessions: " + e.getMessage());
			return MessageHelper.error(ErrorMessages.INTERNAL_ERROR, e.getMessage());
		}
	}
}