package pt.unl.fct.di.adc.firstwebapp.util;

import java.util.UUID;

public class AuthToken {

	public static final long EXPIRATION_TIME = 1000*60*15; // 15min
	//public static final long EXPIRATION_TIME = 1000*60*60*24*7; //1 semana(so para testes)
	
	public String username;
	public String tokenID;
	public long creationDate;
	public long expirationDate;
	
	public AuthToken() { }
	
	public AuthToken(String username) {
		this.username = username;
		this.tokenID = UUID.randomUUID().toString();
		this.creationDate = System.currentTimeMillis();
		this.expirationDate = this.creationDate + EXPIRATION_TIME;
	}
	
}
