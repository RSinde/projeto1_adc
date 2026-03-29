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

        if(tokenEntity == null) {
            return MessageHelper.error(ErrorMessages.INVALID_TOKEN, ErrorMessages.INVALID_TOKEN_MSG);
        }
        if(tokenEntity.getLong("expirationDate") < System.currentTimeMillis()){
            return MessageHelper.error(ErrorMessages.TOKEN_EXPIRED,ErrorMessages.TOKEN_EXPIRED_MSG);
        }
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
            LOG.severe("Error querying users: " + e.getMessage());
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
            // 2. Criar as chaves e ir ao Datastore (Usar tokenId minúsculo)
            Key userKey = datastore.newKeyFactory().setKind("User").newKey(data.username);
            Entity userEntity = datastore.get(userKey);

            Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(request.token.tokenID);
            Entity tokenEntity = datastore.get(tokenKey);

            Response error;
            if ((error = AuthUtils.validateUser(userEntity)) != null) return error;
            if ((error = AuthUtils.validateToken(tokenEntity)) != null) return error;

            int callerWeight = AuthUtils.getRoleWeight(tokenEntity.getString("user_role"));

            if (callerWeight < 3) { // Peso 3 é o ADMIN na tua lógica
                return MessageHelper.error(ErrorMessages.UNAUTHORIZED, ErrorMessages.UNAUTHORIZED_MSG);
            }

            // Impede que um Admin se apague a si próprio por acidente
            if(userEntity.getKey().getName().equals(tokenEntity.getString("username"))){
                return MessageHelper.error(ErrorMessages.FORBIDDEN, ErrorMessages.FORBIDDEN_MSG);
            }

            datastore.delete(userKey);

            Map<String, String> successData = new LinkedHashMap<>();
            successData.put("message", "Account deleted successfully");
            return MessageHelper.success(successData);

        } catch (Exception e) {
            LOG.severe("Crash no deleteAccount: " + e.getMessage());
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

            Response error;
            if ((error = AuthUtils.validateUser(userEntity)) != null) return error;
            if ((error = AuthUtils.validateToken(tokenEntity)) != null) return error;

            String callerUsername = tokenEntity.getString("username");
            String callerRole = tokenEntity.getString("user_role");
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
            return MessageHelper.error("INTERNAL_ERROR", "A technical error occurred.");
        }
    }









    @GET
    @Path("/listallattributes")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listAllAttributes() {
        try {
            // 1. Obter todos os UTILIZADORES (tabela User)
            Query<Entity> userQuery = Query.newEntityQueryBuilder().setKind("User").build();
            QueryResults<Entity> userResults = datastore.run(userQuery);

            List<Map<String, Object>> resultList = new ArrayList<>();

            while (userResults.hasNext()) {
                Entity user = userResults.next();
                String username = user.getKey().getName();

                Map<String, Object> userData = new LinkedHashMap<>();
                // Atributos base do utilizador
                userData.put("username", username);
                userData.put("role", user.contains("user_role") ? user.getString("user_role") : "N/A");
                userData.put("phone", user.contains("user_phone") ? user.getString("user_phone") : "N/A");
                userData.put("address", user.contains("user_address") ? user.getString("user_address") : "N/A");
                userData.put("pwd_hash", user.getString("user_pwd"));

                // 2. Procurar TOKENS associados a este username (tabela Token)
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

                    // Verificação de segurança para o nome do campo de expiração
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

            // Retorna a lista de todos os utilizadores (com ou sem tokens)
            return MessageHelper.success(resultList);

        } catch (Exception e) {
            return MessageHelper.error("INTERNAL_ERROR", "Erro ao listar atributos: " + e.getMessage());
        }
    }
}
