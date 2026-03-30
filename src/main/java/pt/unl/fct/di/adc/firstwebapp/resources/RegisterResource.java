package pt.unl.fct.di.adc.firstwebapp.resources;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.codec.digest.DigestUtils;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.google.gson.Gson;
import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Transaction;
import com.google.cloud.datastore.DatastoreOptions;

import pt.unl.fct.di.adc.firstwebapp.util.*;

@Path("/")
public class RegisterResource {

	private static final Logger LOG = Logger.getLogger(RegisterResource.class.getName());
    private static final Datastore datastore = DatastoreOptions.newBuilder()
            .setProjectId("projeto-adc-491613")
            .build()
            .getService();

	private final Gson g = new Gson();


	public RegisterResource() {}	// Default constructor, nothing to do


    @POST
    @Path("/createaccount")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createAccount(RequestWrapper<RegisterData> request) {
        if(request == null || request.input == null) {
            return MessageHelper.error(ErrorMessages.INVALID_INPUT, ErrorMessages.INVALID_INPUT_MSG);
        }

        RegisterData data = request.input;
        String username = data.username.trim();
        LOG.fine("Attempt to register user: " + data.username);

        if(!data.validRegistration())
            return MessageHelper.error(ErrorMessages.INVALID_INPUT, ErrorMessages.INVALID_INPUT_MSG);

        try {
            Transaction txn = datastore.newTransaction();
            Key userKey = datastore.newKeyFactory().setKind("User").newKey(username);
            Entity user = txn.get(userKey);

            if(user != null) {
                txn.rollback();
                return MessageHelper.error(ErrorMessages.USER_ALREADY_EXISTS, ErrorMessages.USER_ALREADY_EXISTS_MSG);
            }
            else {
                user = Entity.newBuilder(userKey)
                        .set("user_pwd", DigestUtils.sha512Hex(data.password))
                        .set("user_phone", data.phone)
						.set("user_address", data.address)
						.set("user_role", data.role)
                        .set("user_creation_time", Timestamp.now())
                        .build();
                txn.put(user);
                txn.commit();
                LOG.info("User registered " + username);

				Map<String, String> successData = new LinkedHashMap<>();
				successData.put("username", username);
				successData.put("role", data.role);

                return MessageHelper.success(successData);
            }
        }
        catch (Exception e) {
            LOG.severe("Error registering user: " + e.getMessage());
            return MessageHelper.error(ErrorMessages.INTERNAL_ERROR, ErrorMessages.INTERNAL_ERROR_MSG);
        }
    }
}