package pt.unl.fct.di.adc.firstwebapp.util;

import jakarta.ws.rs.core.Response;
import com.google.cloud.datastore.Entity;

public class AuthUtils {

    public static Response validateUser(Entity userEntity) {
        if (userEntity == null) {
            return MessageHelper.error(ErrorMessages.USER_NOT_FOUND, ErrorMessages.USER_NOT_FOUND_MSG);
        }
        return null;
    }

    public static Response validateToken(Entity tokenEntity) {
        if (tokenEntity == null) {
            return MessageHelper.error(ErrorMessages.INVALID_TOKEN, ErrorMessages.INVALID_TOKEN_MSG);
        }

        if (tokenEntity.getLong("expirationDate") < System.currentTimeMillis()) {
            return MessageHelper.error(ErrorMessages.TOKEN_EXPIRED, ErrorMessages.TOKEN_EXPIRED_MSG);
        }
        return null;
    }

    public static int getRoleWeight(String role) {
        if (role == null) return 0;
        switch (role) {
            case "ADMIN":    return 3;
            case "BOFFICER": return 2;
            case "USER":     return 1;
            default:         return 0;
        }
    }
}