package org.shark.mentor.mcp.service;



import dev.langchain4j.model.chat.ChatLanguageModel;
import org.shark.mentor.mcp.config.LlmProperties;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LlmServiceLangchain4j implements LlmService {

    private final LlmProperties props;

    private ChatLanguageModel chatModel;

    @jakarta.annotation.PostConstruct
    public void initModel() {
        chatModel = LlmFactory.createChatModel(
                props.getProvider(),
                props.getModel(),
                props.getApi().getBaseUrl(),
                props.getApi().getKey()
        );
    }

    @Override
    public String generate(String mcpAnswer, String context) {
        String prompt = "Reformula la siguiente respuesta en lenguaje natural, sin agregar ni inferir información. Solo usa el contenido proporcionado:\n\n"
                + mcpAnswer;
        return chatModel.generate(prompt);
    }
}