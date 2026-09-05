package com.prenotazioni.security;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;

/**
 * Costruisce la chiave di firma dal segreto configurato, accettando entrambi gli alfabeti
 * base64.
 *
 * Esiste per un difetto reale e ricorrente: il codice decodificava in base64URL, che usa
 * '-' e '_', mentre la documentazione diceva ovunque di generare il segreto con
 *
 *     openssl rand -base64 48
 *
 * che emette base64 STANDARD, con '+' e '/'. Su 64 caratteri casuali la probabilita' di non
 * incontrare nessuno dei due e' circa il 13%: il comando documentato produceva un segreto
 * inservibile quasi nove volte su dieci, e il messaggio d'errore
 * ("Illegal base64url character: '/'") non diceva a nessuno che il problema era il modo in
 * cui il segreto era stato generato.
 *
 * Normalizzare invece di scegliere un alfabeto e' la soluzione giusta perche' un segreto e'
 * qualcosa che una persona incolla: pretendere che sappia quale delle due varianti serva e'
 * un requisito che non si puo' far rispettare, e che fallisce in modo oscuro.
 *
 * DEVE stare in un punto solo: chi firma (auth-service) e chi verifica (tutti gli altri)
 * devono derivare la stessa identica chiave dallo stesso segreto. Due normalizzazioni
 * leggermente diverse produrrebbero token che nessuno riesce a validare.
 */
public final class JwtKey {

    private JwtKey() {
    }

    public static SecretKey da(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret non configurato: impostare JWT_SECRET nell'ambiente o in .env");
        }
        // Si porta tutto su base64url, l'alfabeto che il decoder si aspetta. Un segreto
        // gia' in base64url attraversa questa riga immutato.
        String normalizzato = secret.trim().replace('+', '-').replace('/', '_');
        return Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(normalizzato));
    }
}
