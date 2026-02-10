package com.back.global.security;

public class JwtKeys {
}

//package com.blue.global.security;
//
//import io.jsonwebtoken.io.Decoders;
//import io.jsonwebtoken.security.Keys;
//import org.springframework.stereotype.Component;
//import org.springframework.beans.factory.annotation.Value;
//
//import javax.crypto.SecretKey;
//
//@Component
//public class JwtKeys {
//  private final SecretKey accessKey;
//  private final SecretKey refreshKey;
//
//  public JwtKeys(
//      @Value("${jwt.access.secret}") String accessSecretB64,
//      @Value("${jwt.refresh.secret}") String refreshSecretB64) {
//
//    this.accessKey  = Keys.hmacShaKeyFor(Decoders.BASE64.decode(accessSecretB64));
//    this.refreshKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(refreshSecretB64));
//  }
//
//  public SecretKey accessKey()  { return accessKey; }
//  public SecretKey refreshKey() { return refreshKey; }
//}
