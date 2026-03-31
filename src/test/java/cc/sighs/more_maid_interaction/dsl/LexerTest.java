package cc.sighs.more_maid_interaction.dsl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class LexerTest {
    @Test
    public void lexKeywordsAndOperators() {
        String src = """
                module \"m\"
                fn f(a,b){ let x = a + b * 2; if x >= 3 && true { stop } }
                # comment
                on_event \"builtin.player.gift_flower\" { say(\"ok\") }
                """;
        MaidscriptLexer lexer = new MaidscriptLexer(src);
        List<Token> tokens = lexer.lex();
        Assertions.assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.MODULE));
        Assertions.assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.FN));
        Assertions.assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.ON_EVENT));
        Assertions.assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.AND_AND));
        Assertions.assertEquals(TokenType.EOF, tokens.get(tokens.size() - 1).type());
    }

    @Test
    public void lexStringEscapes() {
        String src = "say(\"a\\n\\\"b\\\"\")";
        MaidscriptLexer lexer = new MaidscriptLexer(src);
        List<Token> tokens = lexer.lex();
        Token str = tokens.stream().filter(t -> t.type() == TokenType.STRING).findFirst().orElseThrow();
        Assertions.assertEquals("a\n\"b\"", str.literal());
    }
}
