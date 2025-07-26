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
    public String generate(String question, String context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Responde \u00fanicamente utilizando la informaci\u00f3n del contexto proporcionado. ")
              .append("No infieras ni agregues datos que no est\u00e9n en el contexto.");

        if (context != null && !context.isBlank()) {
            prompt.append("\n\nContexto:\n").append(context);
        }

        prompt.append("\n\nPregunta del usuario: ").append(question);

        return chatModel.generate(prompt.toString());
    }
}