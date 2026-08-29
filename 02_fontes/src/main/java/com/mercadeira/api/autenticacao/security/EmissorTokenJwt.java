package com.mercadeira.api.autenticacao.security;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.mercadeira.api.autenticacao.application.TokenAutenticacao;
import com.mercadeira.api.usuario.domain.Usuario;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

@Component
public class EmissorTokenJwt {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public EmissorTokenJwt(JwtEncoder jwtEncoder, JwtProperties jwtProperties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    public TokenAutenticacao emitirPara(Usuario usuario) {
        Instant emitidoEm = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiraEm = emitidoEm.plus(jwtProperties.getDuracao());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(usuario.getId().toString())
                .issuedAt(emitidoEm)
                .expiresAt(expiraEm)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenAutenticacao(token, expiraEm);
    }
}
