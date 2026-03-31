package cc.sighs.more_maid_interaction.dsl;

import cc.sighs.more_maid_interaction.dsl.ast.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ParserTest {
    @Test
    public void parseProgramWithFunctionAndEvent() {
        String src = """
                module "global"
                config { trace: true }

                fn speak_by_mood(soft, neutral, cold) {
                  if mood.top == "AFFECTIONATE" {
                    say(soft)
                  } else {
                    say(cold)
                  }
                }

                on_event "builtin.player.knee_pillow" {
                  let trust = state.bond * 0.6 + state.sincerity * 0.4
                  apply_delta { favor: trust }
                  stop
                }
                """;

        Program program = MaidscriptParser.parse(src);
        Assertions.assertEquals(2, program.headers().size());
        Assertions.assertEquals(2, program.declarations().size());
        Assertions.assertInstanceOf(FunctionDecl.class, program.declarations().get(0));
        Assertions.assertInstanceOf(EventDecl.class, program.declarations().get(1));

        EventDecl event = (EventDecl) program.declarations().get(1);
        Assertions.assertEquals("builtin.player.knee_pillow", event.eventId());
        Assertions.assertFalse(event.body().statements().isEmpty());

        ExprStmt cmd = (ExprStmt) event.body().statements().get(1);
        Assertions.assertInstanceOf(CallExpr.class, cmd.expression());
        CallExpr call = (CallExpr) cmd.expression();
        Assertions.assertNotNull(call.blockArg());
    }
}
