package pt.unl.fct.di.adc.firstwebapp.resources;

import java.util.*;
import java.util.logging.Logger;

import com.google.api.client.util.store.MemoryDataStoreFactory;
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
public class UserResource {

    private static final Logger LOG = Logger.getLogger(LoginResource.class.getName());
    private static final Datastore datastore = DatastoreOptions.newBuilder()
            .setProjectId("projeto-adc-491613")
            .build()
            .getService();
    private static final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");

    public UserResource() {
    }

    @POST
    @Path("/showusers")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response showUsers(RequestWrapper<Void> request) {
        LOG.info("Showing users");

        Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(request.token.tokenID);
        Entity tokenEntity = datastore.get(tokenKey);

        Response error;
        if ((error = AuthUtils.validateToken(tokenEntity)) != null) return error;
        String role = tokenEntity.getString("user_role");
        if("USER".equals(role)){
            return MessageHelper.error(ErrorMessages.UNAUTHORIZED, ErrorMessages.UNAUTHORIZED_MSG);
        }

        try{
            Query<Entity> query = Query.newEntityQueryBuilder()
                    .setKind("User")
                    .build();
            QueryResults<Entity> results = datastore.run(query);

            List<Map<String, String>> users = new ArrayList<>();
            while (results.hasNext()) {
                Entity user = results.next();
                Map<String, String> userMap = new LinkedHashMap<>();
                userMap.put("username", user.getKey().getName());
                userMap.put("role", user.getString("user_role"));
                users.add(userMap);

            }
            LOG.info("Users listed successfully by: " + tokenEntity.getString("username"));
            return MessageHelper.success(users);
        } catch(Exception e) {
            LOG.severe("Error in showUsers: " + e.getMessage());
            return MessageHelper.error("INTERNAL_ERROR", "Could not fetch user list.");
        }
    }


    @POST
    @Path("/deleteaccount")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteAccount(RequestWrapper<LoginData> request) {
        if(request == null || request.token == null || request.token.tokenID == null || request.input == null){
            return MessageHelper.error(ErrorMessages.INVALID_INPUT, ErrorMessages.INVALID_INPUT_MSG);
        }
        LoginData data = request.input;
        try {
            Key userKey = datastore.newKeyFactory().setKind("User").newKey(data.username);
            Entity userEntity = datastore.get(userKey);

            Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(request.token.tokenID);
            Entity tokenEntity = datastore.get(tokenKey);

            String username = tokenEntity.getString("username");
            Key callerUserKey = datastore.newKeyFactory().setKind("User").newKey(username);
            Entity callerUserEntity = datastore.get(callerUserKey);

            Response error;
            if ((error = AuthUtils.validateUser(userEntity)) != null) return error;
            if ((error = AuthUtils.validateToken(tokenEntity)) != null) return error;

            int callerWeight = AuthUtils.getRoleWeight(callerUserEntity.getString("user_role"));

            if (callerWeight < 3) {
                return MessageHelper.error(ErrorMessages.UNAUTHORIZED, ErrorMessages.UNAUTHORIZED_MSG);
            }

            if(userEntity.getKey().getName().equals(tokenEntity.getString("username"))){
                return MessageHelper.error(ErrorMessages.FORBIDDEN, ErrorMessages.FORBIDDEN_MSG);
            }

            Query<Entity> tokenQuery = Query.newEntityQueryBuilder()
                    .setKind("Token")
                    .setFilter(PropertyFilter.eq("username", data.username))
                    .build();
            QueryResults<Entity> results = datastore.run(tokenQuery);
            List<Key> keysToDelete = new ArrayList<>();
            while (results.hasNext()) {
                keysToDelete.add(results.next().getKey());
            }
            if (!keysToDelete.isEmpty()) {
                datastore.delete(keysToDelete.toArray(new Key[0]));
            }

            datastore.delete(userKey);

            Map<String, String> successData = new LinkedHashMap<>();
            successData.put("message", "Account deleted successfully");
            return MessageHelper.success(successData);

        } catch (Exception e) {
            LOG.severe("Error in deleteAccount: " + e.getMessage());
            return MessageHelper.error(ErrorMessages.INTERNAL_ERROR, ErrorMessages.INTERNAL_ERROR_MSG);
        }
    }


    @POST
    @Path("/modaccount")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response modifyAccount(RequestWrapper<UpdateData> request) {
        if(request == null || request.token == null || request.input == null || request.input.username == null || request.input.username.trim().isEmpty()) {
            return MessageHelper.error(ErrorMessages.INVALID_INPUT, ErrorMessages.INVALID_INPUT_MSG);
        }

        UpdateData data = request.input;

        if(data.attributes != null && data.attributes.username!=null) {
            return MessageHelper.error(ErrorMessages.FORBIDDEN, ErrorMessages.FORBIDDEN_MSG);
        }

        try{
            Key userKey = datastore.newKeyFactory().setKind("User").newKey(data.username);
            Entity userEntity = datastore.get(userKey);

            Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(request.token.tokenID);
            Entity tokenEntity = datastore.get(tokenKey);

            String username = tokenEntity.getString("username");
            Key callerUserKey = datastore.newKeyFactory().setKind("User").newKey(username);
            Entity callerUserEntity = datastore.get(callerUserKey);

            Response error;
            if ((error = AuthUtils.validateUser(userEntity)) != null) return error;
            if ((error = AuthUtils.validateToken(tokenEntity)) != null) return error;

            String callerUsername = tokenEntity.getString("username");
            String callerRole = callerUserEntity.getString("user_role");
            String targetUsername = userEntity.getKey().getName();
            String targetRole = userEntity.getString("user_role");

            int callerWeight = AuthUtils.getRoleWeight(callerRole);
            int targetWeight = AuthUtils.getRoleWeight(targetRole);
            boolean isSelf = callerUsername.equals(targetUsername);

            if (!isSelf && callerWeight <= targetWeight) {
                return MessageHelper.error(ErrorMessages.UNAUTHORIZED, ErrorMessages.UNAUTHORIZED_MSG);
            }

            Transaction txn = datastore.newTransaction();

            Entity user = txn.get(userKey);
            Entity.Builder updateBuilder = Entity.newBuilder(user);

            if (data.attributes.phone != null) updateBuilder.set("user_phone", data.attributes.phone);
            if (data.attributes.address != null) updateBuilder.set("user_address", data.attributes.address);

            txn.put(updateBuilder.build());
            txn.commit();
            LOG.info("Attributes updated for " + targetUsername + " by " + callerUsername);

            Map<String, String> success = new LinkedHashMap<>();
            success.put("message", "Updated successfully");
            return MessageHelper.success(success);
        } catch (Exception e) {
            LOG.severe("Error in modAccount: " + e.getMessage());
            return MessageHelper.error(ErrorMessages.INTERNAL_ERROR, ErrorMessages.INTERNAL_ERROR_MSG);
        }
    }

    @POST
    @Path("/showuserrole")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response showUserRole(RequestWrapper<LoginData> request) {
        if(request == null || request.token == null || request.token.tokenID == null ||
                request.input == null || request.input.username == null) {
            return MessageHelper.error(ErrorMessages.INVALID_INPUT, ErrorMessages.INVALID_INPUT_MSG);
        }

        String targetUsername = request.input.username;

        try {
            Key callerTokenKey = datastore.newKeyFactory().setKind("Token").newKey(request.token.tokenID);
            Entity callerTokenEntity = datastore.get(callerTokenKey);

            Key targetUserKey = datastore.newKeyFactory().setKind("User").newKey(targetUsername);
            Entity targetUserEntity = datastore.get(targetUserKey);

            String username = callerTokenEntity.getString("username");
            Key callerUserKey = datastore.newKeyFactory().setKind("User").newKey(username);
            Entity callerUserEntity = datastore.get(callerUserKey);

            Response error;
            if ((error = AuthUtils.validateToken(callerTokenEntity)) != null) return error;
            if ((error = AuthUtils.validateUser(targetUserEntity)) != null) return error;

            String callerRole = callerUserEntity.getString("user_role");
            String targetRole = targetUserEntity.getString("user_role");

            int callerWeight = AuthUtils.getRoleWeight(callerRole);
            int targetWeight = AuthUtils.getRoleWeight(targetRole);

            if (callerWeight < targetWeight) {
                return MessageHelper.error(ErrorMessages.UNAUTHORIZED, ErrorMessages.UNAUTHORIZED_MSG);
            }

            Map<String, String> result = new LinkedHashMap<>();
            result.put("username", targetUsername);
            result.put("role", targetRole);

            LOG.info("Role of " + targetUsername + " shown to " + callerTokenEntity.getString("username"));
            return MessageHelper.success(result);

        } catch (Exception e) {
            LOG.severe("Error in showUserRole: " + e.getMessage());
            return MessageHelper.error(ErrorMessages.INTERNAL_ERROR, e.getMessage());
        }
    }

    @POST
    @Path("/changeuserrole")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response changeUserRole(RequestWrapper<RoleData> request) {
        if(request == null || request.token == null || request.token.tokenID == null ||
                request.input == null || request.input.username == null || request.input.newRole == null) {
            return MessageHelper.error(ErrorMessages.INVALID_INPUT, ErrorMessages.INVALID_INPUT_MSG);
        }

        RoleData data = request.input;

        if(!(data.newRole.equals("USER") ||  data.newRole.equals("ADMIN")|| data.newRole.equals("BOFFICER"))){
            return MessageHelper.error(ErrorMessages.FORBIDDEN, ErrorMessages.FORBIDDEN_MSG);
        }

        try {
            Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(request.token.tokenID);
            Entity tokenEntity = datastore.get(tokenKey);

            Response error;
            if ((error = AuthUtils.validateToken(tokenEntity)) != null) return error;

            String username = tokenEntity.getString("username");
            Key callerUserKey = datastore.newKeyFactory().setKind("User").newKey(username);
            Entity callerUserEntity = datastore.get(callerUserKey);

            String callerRole = callerUserEntity.getString("user_role");

            if (AuthUtils.getRoleWeight(callerRole) < 3) {
                return MessageHelper.error(ErrorMessages.UNAUTHORIZED, ErrorMessages.UNAUTHORIZED_MSG);
            }

            if (tokenEntity.getString("username").equals(data.username)) {
                return MessageHelper.error(ErrorMessages.FORBIDDEN, ErrorMessages.FORBIDDEN_MSG);
            }

            Query<Entity> tokenQuery = Query.newEntityQueryBuilder()
                    .setKind("Token")
                    .setFilter(PropertyFilter.eq("username", data.username))
                    .build();
            QueryResults<Entity> targetTokens = datastore.run(tokenQuery);
            List<Entity> tokensToUpdate = new ArrayList<>();
            while (targetTokens.hasNext()) {
                tokensToUpdate.add(Entity.newBuilder(targetTokens.next()).set("user_role", data.newRole).build());
            }

            Transaction txn = datastore.newTransaction();
            try {
                Key targetUserKey = datastore.newKeyFactory().setKind("User").newKey(data.username);
                Entity targetUserEntity = txn.get(targetUserKey);
                if ((error = AuthUtils.validateUser(targetUserEntity)) != null) return error;

                if (targetUserEntity.getString("user_role").equals(data.newRole)) {
                    return MessageHelper.error(ErrorMessages.FORBIDDEN, ErrorMessages.FORBIDDEN_MSG);
                }

                Entity.Builder updateBuilder = Entity.newBuilder(targetUserEntity);
                updateBuilder.set("user_role", data.newRole);

                txn.put(updateBuilder.build());

                if (!tokensToUpdate.isEmpty()) {
                    txn.put(tokensToUpdate.toArray(new Entity[0]));
                }

                txn.commit();

                LOG.info("Role updated successfully for user: " + data.username);
                return MessageHelper.success("Role updated successfully");
            } finally {
                if (txn.isActive()) txn.rollback();
            }
        } catch (Exception e) {
            LOG.severe("Error in changeUserRole: " + e.getMessage());
            return MessageHelper.error(ErrorMessages.INTERNAL_ERROR, e.getMessage());
        }
    }

    @POST
    @Path("/changeuserpwd")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response changeUserPassword(RequestWrapper<PasswordData> request) {
        if(request == null || request.token == null || request.token.tokenID == null ||
                request.input == null || request.input.username == null ||
                request.input.oldPassword == null || request.input.newPassword == null) {
            return MessageHelper.error(ErrorMessages.INVALID_INPUT, ErrorMessages.INVALID_INPUT_MSG);
        }

        PasswordData data = request.input;

        try {
            Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(request.token.tokenID);
            Entity tokenEntity = datastore.get(tokenKey);

            Response error;
            if ((error = AuthUtils.validateToken(tokenEntity)) != null) return error;

            if (!tokenEntity.getString("username").equals(data.username)) {
                return MessageHelper.error(ErrorMessages.UNAUTHORIZED, ErrorMessages.UNAUTHORIZED_MSG);
            }

            Key userKey = datastore.newKeyFactory().setKind("User").newKey(data.username);
            Entity userEntity = datastore.get(userKey);
            if ((error = AuthUtils.validateUser(userEntity)) != null) return error;

            String hashedOld = DigestUtils.sha512Hex(data.oldPassword);
            if (!userEntity.getString("user_pwd").equals(hashedOld)) {
                return MessageHelper.error(ErrorMessages.INVALID_CREDENTIALS, ErrorMessages.INVALID_CREDENTIALS_MSG);
            }

            if (data.newPassword.equals(data.oldPassword)) {
                return MessageHelper.error(ErrorMessages.FORBIDDEN, ErrorMessages.FORBIDDEN_MSG);
            }

            Transaction txn = datastore.newTransaction();
            try {
                Entity user = txn.get(userKey);
                Entity.Builder updateBuilder = Entity.newBuilder(user);
                updateBuilder.set("user_pwd", DigestUtils.sha512Hex(data.newPassword));

                txn.put(updateBuilder.build());
                txn.commit();

                LOG.info("Password self-updated successfully for user: " + data.username);
                return MessageHelper.success("Password changed successfully");
            } finally {
                if (txn.isActive()) txn.rollback();
            }
        } catch (Exception e) {
            LOG.severe("Error in changePassword: " + e.getMessage());
            return MessageHelper.error(ErrorMessages.INTERNAL_ERROR, e.getMessage());
        }
    }

    @POST
    @Path("/logout")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doLogout(RequestWrapper<LoginData> request) {
        if(request == null || request.token == null || request.token.tokenID == null ||
                request.input == null || request.input.username == null) {
            return MessageHelper.error(ErrorMessages.INVALID_INPUT, ErrorMessages.INVALID_INPUT_MSG);
        }

        String targetUsername = request.input.username;

        try {
            Key callerTokenKey = datastore.newKeyFactory().setKind("Token").newKey(request.token.tokenID);
            Entity callerTokenEntity = datastore.get(callerTokenKey);

            String username = callerTokenEntity.getString("username");
            Key callerUserKey = datastore.newKeyFactory().setKind("User").newKey(username);
            Entity callerUserEntity = datastore.get(callerUserKey);

            Response error;

            if ((error = AuthUtils.validateToken(callerTokenEntity)) != null) return error;

            String callerUsername = callerTokenEntity.getString("username");
            String callerRole = callerUserEntity.getString("user_role");
            int callerWeight = AuthUtils.getRoleWeight(callerRole); //

            boolean isSelf = callerUsername.equals(targetUsername);

            if (!isSelf && callerWeight < 3) {
                return MessageHelper.error(ErrorMessages.UNAUTHORIZED, ErrorMessages.UNAUTHORIZED_MSG);
            }

            Query<Entity> query = Query.newEntityQueryBuilder()
                    .setKind("Token")
                    .setFilter(PropertyFilter.eq("username", targetUsername))
                    .build();
            QueryResults<Entity> results = datastore.run(query);

            List<Key> keysToDelete = new ArrayList<>();
            while (results.hasNext()) {
                keysToDelete.add(results.next().getKey());
            }

            if (!keysToDelete.isEmpty()) {
                datastore.delete(keysToDelete.toArray(new Key[0]));
            }

            LOG.info("Log out done to " + targetUsername + " by " + callerUsername);
            return MessageHelper.success("Logout successful");

        } catch (Exception e) {
            LOG.severe("Error in logout: " + e.getMessage());
            return MessageHelper.error(ErrorMessages.INTERNAL_ERROR, ErrorMessages.INTERNAL_ERROR_MSG);
        }
    }


    /*  USADO PARA TESTES
    @GET
    @Path("/listallattributes")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listAllAttributes() {
        try {
            Query<Entity> userQuery = Query.newEntityQueryBuilder().setKind("User").build();
            QueryResults<Entity> userResults = datastore.run(userQuery);

            List<Map<String, Object>> resultList = new ArrayList<>();

            while (userResults.hasNext()) {
                Entity user = userResults.next();
                String username = user.getKey().getName();

                Map<String, Object> userData = new LinkedHashMap<>();
                userData.put("username", username);
                userData.put("role", user.contains("user_role") ? user.getString("user_role") : "N/A");
                userData.put("phone", user.contains("user_phone") ? user.getString("user_phone") : "N/A");
                userData.put("address", user.contains("user_address") ? user.getString("user_address") : "N/A");
                userData.put("pwd_hash", user.getString("user_pwd"));

                Query<Entity> tokenQuery = Query.newEntityQueryBuilder()
                        .setKind("Token")
                        .setFilter(PropertyFilter.eq("username", username))
                        .build();
                QueryResults<Entity> tokenResults = datastore.run(tokenQuery);

                List<Map<String, Object>> userTokens = new ArrayList<>();
                while (tokenResults.hasNext()) {
                    Entity token = tokenResults.next();
                    Map<String, Object> tMap = new LinkedHashMap<>();
                    tMap.put("tokenID", token.getKey().getName());

                    String expiresStr = "N/A";
                    if (token.contains("expiration_date")) {
                        expiresStr = new Date(token.getLong("expiration_date")).toString();
                    } else if (token.contains("expirationDate")) {
                        expiresStr = new Date(token.getLong("expirationDate")).toString();
                    } else if (token.contains("expirationData")) {
                        expiresStr = new Date(token.getLong("expirationData")).toString();
                    }

                    tMap.put("expires", expiresStr);
                    userTokens.add(tMap);
                }

                userData.put("tokens", userTokens);
                resultList.add(userData);
            }

            return MessageHelper.success(resultList);

        } catch (Exception e) {
            return MessageHelper.error("INTERNAL_ERROR", "Erro ao listar atributos: " + e.getMessage());
        }
    }
    */
}
