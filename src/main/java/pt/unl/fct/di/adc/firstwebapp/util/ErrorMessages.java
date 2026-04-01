package pt.unl.fct.di.adc.firstwebapp.util;

public class ErrorMessages {

    public ErrorMessages() {}

    // Códigos de Erro
    public static final String INVALID_CREDENTIALS = "9900";
    public static final String USER_ALREADY_EXISTS = "9901";
    public static final String USER_NOT_FOUND = "9902";
    public static final String INVALID_TOKEN = "9903";
    public static final String TOKEN_EXPIRED = "9904";
    public static final String UNAUTHORIZED = "9905";
    public static final String INVALID_INPUT = "9906";
    public static final String FORBIDDEN = "9907";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    // Descrições das Mensagens
    public static final String INVALID_CREDENTIALS_MSG = "The username-password pair is not valid";
    public static final String USER_ALREADY_EXISTS_MSG = "Error in creating an account because the username already exists";
    public static final String USER_NOT_FOUND_MSG = "The username referred in the operation doesn't exist in registered accounts";
    public static final String INVALID_TOKEN_MSG = "The operation is called with an invalid token";
    public static final String TOKEN_EXPIRED_MSG = "The operation is called with a token that is expired";
    public static final String UNAUTHORIZED_MSG = "The operation is not allowed for the user role";
    public static final String INVALID_INPUT_MSG = "The call is using input data not following the correct specification";
    public static final String FORBIDDEN_MSG = "The operation generated a forbidden error by other reason";
    public static final String INTERNAL_ERROR_MSG = "A technical error occurred. Please try again later.";

}