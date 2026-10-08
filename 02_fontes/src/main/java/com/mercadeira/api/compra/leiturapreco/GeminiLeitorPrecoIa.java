package com.mercadeira.api.compra.leiturapreco;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(name = "mercadeira.ai-price-reader.provider", havingValue = "gemini")
class GeminiLeitorPrecoIa implements LeitorPrecoIa {
    private final LeitorPrecoIaProperties properties; private final RestClientGeminiClient client; private final ObjectMapper json;
    GeminiLeitorPrecoIa(LeitorPrecoIaProperties p, RestClientGeminiClient c, ObjectMapper j) { properties=p; client=c; json=j; }
    public List<BigDecimal> identificarPrecos(byte[] imagem, String contentType) {
        if (!properties.isEnabled()) throw new LeituraPrecoIaIndisponivelException("A leitura de preco por IA esta desabilitada.");
        if (!StringUtils.hasText(properties.getGeminiApiKey()) || !StringUtils.hasText(properties.getGeminiModel())) throw new LeituraPrecoIaIndisponivelException("Configuracao Gemini incompleta.");
        try { Resposta r=json.readValue(client.identificar(properties.getGeminiApiKey(), properties.getGeminiModel(), imagem, contentType), Resposta.class); if(r==null||r.precos()==null) throw new LeituraPrecoIaRespostaInvalidaException("Resposta Gemini invalida."); LinkedHashSet<BigDecimal> out=new LinkedHashSet<>(); for(BigDecimal p:r.precos()) try { if(p!=null&&p.signum()>0) out.add(p.setScale(2,RoundingMode.UNNECESSARY)); } catch(ArithmeticException ignored) {} return List.copyOf(out).subList(0, Math.min(20,out.size())); } catch(LeituraPrecoIaException e){throw e;} catch(Exception e){throw new LeituraPrecoIaRespostaInvalidaException("Resposta Gemini invalida.",e);}
    }
    private record Resposta(List<BigDecimal> precos) {}
}
