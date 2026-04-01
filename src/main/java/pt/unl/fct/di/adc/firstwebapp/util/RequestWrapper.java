package pt.unl.fct.di.adc.firstwebapp.util;

public class RequestWrapper<T> {

    public T input;
    public TokenData token;

    public RequestWrapper() { }

    public RequestWrapper(T input, TokenData token) {
        this.input = input;
        this.token = token;
    }
}