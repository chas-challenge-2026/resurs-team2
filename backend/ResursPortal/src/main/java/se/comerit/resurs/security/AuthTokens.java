package se.comerit.resurs.security;

public record AuthTokens(String accessToken, String refreshToken, PrincipalRole role, String name) {

}
