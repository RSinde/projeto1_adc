package pt.unl.fct.di.adc.firstwebapp.resources;

import java.util.Date;
import java.util.List;
import java.util.Calendar;
import java.util.ArrayList;
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


@Path("/login")
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
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response doLogin(RequestWrapper<LoginData> request) {
		LoginData data = request.input;
		LOG.info("Login attempt for: " + data.username);

		try {
			// 1. Procurar o utilizador no Datastore
			Key userKey = datastore.newKeyFactory().setKind("User").newKey(data.username);
			Entity user = datastore.get(userKey);

			if (user == null) {
				return MessageHelper.error(ErrorMessages.USER_NOT_FOUND, ErrorMessages.USER_NOT_FOUND_MSG);
			}

			// 2. Verificar a password (SHA-512) usando o campo 'user_pwd' que criaste no registo
			String hashedInputPassword = DigestUtils.sha512Hex(data.password);
			if (!user.getString("user_pwd").equals(hashedInputPassword)) {
				return MessageHelper.error(ErrorMessages.INVALID_CREDENTIALS, ErrorMessages.INVALID_CREDENTIALS_MSG);
			}

			// 3. Gerar o AuthToken (o teu construtor gera o UUID automaticamente)
			AuthToken token = new AuthToken(data.username);

			// 4. Guardar o Token no Datastore para validação de pedidos futuros
			Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(token.tokenID);
			Entity tokenEntity = Entity.newBuilder(tokenKey)
					.set("username", token.username)
					.set("creationDate", token.creationDate)
					.set("expirationDate", token.expirationDate)
					.set("user_role", user.getString("user_role"))
					.build();

			datastore.put(tokenEntity);

			LOG.info("Login successful for user: " + data.username);
			return MessageHelper.success(token);

		} catch (Exception e) {
			LOG.severe("Login error: " + e.getMessage());
			return MessageHelper.error(ErrorMessages.INTERNAL_ERROR, ErrorMessages.INTERNAL_ERROR_MSG);
		}
	}
}